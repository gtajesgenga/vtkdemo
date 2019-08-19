package com.example.vtkdemo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Parameter {

    @Setter(AccessLevel.NONE)
    @JsonIgnore
    private Class defaultCasting = Number.class;

    @Setter(AccessLevel.NONE)
    @JsonIgnore
    private Class multidimensionalClass = null;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String multidimensional = null;

    private String casting;

    private String value;

    public void setCasting(String casting) {
        this.casting = casting;
        try {
            this.defaultCasting = Class.forName(this.casting);
        } catch (ClassNotFoundException e) {
            log.error("Casting class doesn't exists.", e);
        }
    }

    public void setMultidimensional(String multidimensional) {
        this.multidimensional = multidimensional;

        if (this.multidimensional != null) {
            try {
                this.multidimensionalClass = Class.forName(this.multidimensional);
            } catch (ClassNotFoundException e) {
                log.error("Casting class doesn't exists.", e);
            }
        }
    }

}
