package com.winmart.wms.entity;

import com.winmart.wms.common.BaseEntity;
import com.winmart.wms.entity.enums.RoleName;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "roles")
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "name", length = 30, unique = true, nullable = false)
    private RoleName name;

    @Column(name = "description")
    private String description;
}
