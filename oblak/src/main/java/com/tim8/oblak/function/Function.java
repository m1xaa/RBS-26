package com.tim8.oblak.function;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Function {
    @Setter
    @Id
    @GeneratedValue
    private Long id;

    private String name;

    @Lob
    private String code;

    private String status;

}