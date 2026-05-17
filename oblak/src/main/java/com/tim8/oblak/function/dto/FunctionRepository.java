package com.tim8.oblak.function.dto;

import com.tim8.oblak.function.Function;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FunctionRepository extends JpaRepository<Function, Long> {
}