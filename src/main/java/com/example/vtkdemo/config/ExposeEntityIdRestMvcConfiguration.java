package com.example.vtkdemo.config;

import com.example.vtkdemo.entity.Pipeline;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurer;
import org.springframework.stereotype.Component;

@Component
public class ExposeEntityIdRestMvcConfiguration implements RepositoryRestConfigurer {

  @Override
  public void configureRepositoryRestConfiguration(RepositoryRestConfiguration config) {
    config.exposeIdsFor(Pipeline.class);
  }
}
