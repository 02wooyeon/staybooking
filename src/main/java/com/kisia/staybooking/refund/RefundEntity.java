package com.kisia.staybooking.refund;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.common.AuditedEntity;
import com.kisia.staybooking.user.UserEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="refunds", uniqueConstraints=@UniqueConstraint(name="uk_refunds_booking",columnNames="booking_id"))
public class RefundEntity extends AuditedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long refundId;
 @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="booking_id",nullable=false) private BookingEntity booking;
 @Column(nullable=false) private long amount;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private RefundStatus status=RefundStatus.REQUESTED;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="processed_by") private UserEntity processedBy;
 private LocalDateTime processedAt; private String reason;
 protected RefundEntity(){}
 public RefundEntity(BookingEntity booking,long amount,String reason){this.booking=booking;this.amount=amount;this.reason=reason;}
 public Long getRefundId(){return refundId;} public BookingEntity getBooking(){return booking;} public long getAmount(){return amount;} public RefundStatus getStatus(){return status;} public UserEntity getProcessedBy(){return processedBy;} public LocalDateTime getProcessedAt(){return processedAt;} public String getReason(){return reason;}
 public void process(UserEntity admin,RefundStatus status,LocalDateTime at){if(this.status!=RefundStatus.REQUESTED)throw new IllegalStateException("Refund already processed");this.status=status;processedBy=admin;processedAt=at;}
}
