package com.tim8.oblak.CloudProject;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class CloudProject {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private String status;

    /**
     * Username vlasnika projekta. Cuvamo samo string, ne ManyToOne referencu,
     * da bismo ostali jednostavni - autorizacija se svodi na poredjenje stringa
     * sa principal-om iz SecurityContext-a.
     */
    private String ownerUsername;
}
