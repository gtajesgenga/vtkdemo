package com.example.vtkdemo.entity;

import com.example.vtkdemo.model.PipelineDto;
import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "pipeline")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineEntity {

    @Id @Column(name = "id") @GeneratedValue
    private Long id;

    @Column(name = "name", columnDefinition = "varchar(128)", length = 128, nullable = false)
    private String name;

    @EqualsAndHashCode.Exclude
    @Column(name = "json", columnDefinition = "text", nullable = false)
    @Convert(converter = PipelineConverter.class)
    private PipelineDto pipelineDto;
}
