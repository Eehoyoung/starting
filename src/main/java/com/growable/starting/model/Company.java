package com.growable.starting.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@NoArgsConstructor
@Entity
@Table(name = "company")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column
    private String companyName;
    @Column
    private String workType;
    @Column
    private String position;
    @Column
    private String startDate;
    @Column
    private String endDate; // 허용되는 null 값입니다.
}