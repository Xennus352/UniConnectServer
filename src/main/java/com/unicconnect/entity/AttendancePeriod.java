package com.unicconnect.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One timetable slot a student received attendance credit for within an
 * attendance decision. A 2-period class can credit 1 or 2 slots; an ABSENT
 * student has none.
 */
@Entity
@Table(name = "attendance_periods",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_attendance_periods",
               columnNames = {"attendance_id", "slot_id"}))
@Getter
@Setter
@NoArgsConstructor
public class AttendancePeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "attendance_period_id")
    private UUID attendancePeriodId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendance_id", nullable = false)
    private Attendance attendance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    private TimeSlot slot;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
