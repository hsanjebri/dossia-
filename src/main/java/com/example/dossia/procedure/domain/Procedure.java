package com.example.dossia.procedure.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "procedures")
@Getter
@Setter
@NoArgsConstructor
public class Procedure {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 200)
    private String slug;

    @Column(name = "title_fr", nullable = false, length = 500)
    private String titleFr;

    @Column(name = "title_ar", nullable = false, length = 500)
    private String titleAr;

    @Column(name = "title_tn", length = 500)
    private String titleTn;

    @Column(name = "description_fr", columnDefinition = "TEXT")
    private String descriptionFr;

    @Column(name = "description_ar", columnDefinition = "TEXT")
    private String descriptionAr;

    @Column(nullable = false, length = 300)
    private String ministry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ProcedureCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Difficulty difficulty;

    @Column(name = "delivery_mode", length = 100)
    private String deliveryMode;

    @Column(name = "processing_time", length = 100)
    private String processingTime;

    @Column(length = 100)
    private String fees;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "source_reference", length = 500)
    private String sourceReference;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProcedureStatus status = ProcedureStatus.DRAFT;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "procedure", cascade = CascadeType.ALL, orphanRemoval = true)
    @jakarta.persistence.OrderBy("sortOrder ASC")
    private List<ProcedureDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "procedure", cascade = CascadeType.ALL, orphanRemoval = true)
    @jakarta.persistence.OrderBy("stepNumber ASC")
    private List<ProcedureStep> steps = new ArrayList<>();

    @OneToMany(mappedBy = "procedure", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OfficeLocation> offices = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "procedure_relations",
            joinColumns = @JoinColumn(name = "procedure_id"),
            inverseJoinColumns = @JoinColumn(name = "related_procedure_id"))
    private Set<Procedure> relatedProcedures = new HashSet<>();

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
