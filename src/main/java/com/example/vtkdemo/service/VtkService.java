package com.example.vtkdemo.service;

import com.example.vtkdemo.client.OrthancClient;
import com.example.vtkdemo.config.ApplicationConfig;
import com.example.vtkdemo.entity.PipelineEntity;
import com.example.vtkdemo.model.FilterDto;
import com.example.vtkdemo.model.Method;
import com.example.vtkdemo.model.Parameter;
import com.example.vtkdemo.repository.PipelineRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.itk.simple.Image;
import org.itk.simple.ImageSeriesReader;
import org.itk.simple.SimpleITK;
import org.itk.simple.VectorString;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class VtkService {

    private final Stack<Image> images = new Stack<>();

    private final ApplicationConfig applicationConfig;

    private final OrthancClient orthancClient;

    private final PipelineRepository pipelineRepository;


    public VtkService(ApplicationConfig applicationConfig, OrthancClient orthancClient, PipelineRepository pipelineRepository) {
        this.applicationConfig = applicationConfig;
        this.orthancClient = orthancClient;
        this.pipelineRepository = pipelineRepository;
    }

    public byte[] execute(String studyId, String serieId, Long pipelineId) throws InvocationTargetException {

        List<String> instances = orthancClient.getInstances(studyId, serieId);

        Optional<PipelineEntity> pipeline = pipelineRepository.findById(pipelineId);

        if (pipeline.isPresent()) {
            Path path = null;

            if (!instances.isEmpty()) {
                path = Paths.get(applicationConfig.getTempFolder(), studyId, serieId);
                if (path.toFile().mkdirs()) {

                    Path finalPath = path;
                    instances.forEach(instance -> {
                        File file = Paths.get(finalPath.toString(), instance).toFile();
                        try {
                            OutputStream os = new FileOutputStream(file);
                            os.write(orthancClient.fetchInstance(instance));
                            os.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
            }

            ImageSeriesReader imageSeriesReader = new ImageSeriesReader();
            final VectorString dicomNames = ImageSeriesReader.getGDCMSeriesFileNames(Objects.requireNonNull(path).toString());
            imageSeriesReader.setFileNames(dicomNames);

            images.push(imageSeriesReader.execute());

            //SimpleITK.show(images.peek(), String.valueOf(images.size()), false);

            for (FilterDto filter : pipeline.get().getPipelineDto().getFilters()) {
                processFilter(filter);
            }

            SimpleITK.writeImage(images.peek(), Paths.get(path.toString(), "out.vtk").toString());
            try {
                return IOUtils.toByteArray(new FileInputStream(new File(Paths.get(path.toString(), "out.vtk").toString())));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new byte[0];
    }

    private void processFilter(FilterDto filter) throws InvocationTargetException {
        try {
            Object instance = Class.forName(filter.getFilterClass()).getConstructor().newInstance();

            log.debug("Processing filter: {}", filter.toString());
            filter.getMethods()
                    .forEach(method -> processMethod(instance, method));

            images.push((Image) instance.getClass().getMethod("execute", images.peek().getClass()).invoke(instance, images.peek()));
            //SimpleITK.show(images.peek(), String.valueOf(images.size()), false);
        } catch (InstantiationException e) {
            log.error("Error creating new instance of '{}'", filter.getFilterClass(), e);
        } catch (NoSuchMethodException e) {
            log.error("Error getting method", e);
        } catch (IllegalAccessException e) {
            log.error("Error on access to method", e);
        } catch (ClassNotFoundException e) {
            log.error("Error getting class", e);
        } finally {
            System.gc();
        }
    }

    private static void processMethod(Object instance, Method method) {
        try {
            log.debug("Processing method: {}", method.toString());
            instance.getClass().getMethod(method.getName(), getParamsTypes(method.getParameters())).invoke(instance, getParamsValues(method.getParameters()));
        } catch (NoSuchMethodException e) {
            log.error("Error getting method", e);
        } catch (IllegalAccessException e) {
            log.error("Error on access to method", e);
        } catch (InvocationTargetException e) {
            log.error("Error on invocation", e);
        }
    }

    private static Object[] getParamsValues(List<Parameter> parameters) {
        return parameters.stream()
                .map(parameter -> {
                    try {
                        log.debug("Getting param value for parameter: {}", parameter.toString());

                        if (ClassUtils.isPrimitiveOrWrapper(parameter.getDefaultCasting())) {

                            return parameter.getDefaultCasting().getMethod("parse" + parameter.getDefaultCasting().getSimpleName(), String.class)
                                    .invoke(null, parameter.getValue());
                        }
                    } catch (NoSuchMethodException e) {
                        log.error("Error getting method", e);
                    } catch (IllegalAccessException e) {
                        log.error("Error on access to method", e);
                    } catch (InvocationTargetException e) {
                        log.error("Error on invocation", e);
                    }

                    return parameter.getDefaultCasting().cast(processValue(parameter));
                }).toArray();
    }

    private static Object processValue(Parameter parameter) {
        if (parameter.getMultidimensional() != null) {
            try {
                log.debug("Processing values for parameter: {}", parameter.toString());
                String[] strArray = parameter.getValue().replace("[", "").replace("]", "").split(",");

                Object instance = parameter.getDefaultCasting().getConstructor(long.class).newInstance(strArray.length);

                var ref = new Object() {
                    int i = 0;
                };
                Stream.of(strArray)
                        .forEach(s -> {
                            try {
                                instance.getClass().getMethod("set", int.class, parameter.getMultidimensionalClass()
                                        .getMethod(parameter.getMultidimensionalClass().getSimpleName().toLowerCase().replace("integer", "int") + "Value").getReturnType())
                                        .invoke(instance, ref.i, parameter.getMultidimensionalClass().getMethod("valueOf", String.class).invoke(null, s));
                            ref.i++;
                            } catch (IllegalAccessException e) {
                                e.printStackTrace();
                            } catch (InvocationTargetException e) {
                                log.error("Error on invocation", e);
                            } catch (NoSuchMethodException e) {
                                log.error("Error getting method", e);
                            }
                        });

                return instance;

            } catch (NoSuchMethodException e) {
                log.error("Error getting method", e);
            } catch (IllegalAccessException e) {
                log.error("Error on access to method", e);
            } catch (InvocationTargetException e) {
                log.error("Error on invocation", e);
            } catch (InstantiationException e) {
                log.error("Error creating new instance of '{}'", parameter.getDefaultCasting().getCanonicalName(), e);
            }
        }
        return parameter.getValue();
    }

    private static Class<?>[] getParamsTypes(List<Parameter> parameters) {
        return parameters.stream()
                .map(Parameter::getDefaultCasting)
                .map(clazz -> {
                    try {

                        if (ClassUtils.isPrimitiveOrWrapper(clazz))
                            return clazz.getMethod(clazz.getSimpleName().toLowerCase().replace("integer", "int") + "Value").getReturnType();
                    } catch (NoSuchMethodException e) {
                        log.error("Error getting method", e);
                    }
                    return clazz;
                })
                .collect(Collectors.toUnmodifiableList()).toArray(new Class[0]);
    }

}
