package com.kisia.staybooking.refund;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.common.AuditedEntity;
import com.kisia.staybooking.user.UserEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name="refunds", uniqueConstraints=@UniqueConstraint(name="uk_refunds_booking",columnNames="booking_id"))
public class RefundEntity extends AuditedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long refundId;
 @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="booking_id",nullable=false) private BookingEntity booking;
 @Column(nullable=false) private long amount;
 @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
 @Column(nullable=false,length=30) private RefundStatus status=RefundStatus.REQUESTED;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="processed_by") private UserEntity processedBy;
 private LocalDateTime processedAt; private String reason;
 private LocalDateTime startedAt;
 @Column(length=80) private String failureCode;
 @Column(name="auto_processing") private Boolean autoProcessing;
 protected RefundEntity(){}
 public RefundEntity(BookingEntity booking,long amount,String reason){this.booking=booking;this.amount=amount;this.reason=reason;this.autoProcessing=true;}
 public Long getRefundId(){return refundId;} public BookingEntity getBooking(){return booking;} public long getAmount(){return amount;} public RefundStatus getStatus(){return status;} public UserEntity getProcessedBy(){return processedBy;} public LocalDateTime getProcessedAt(){return processedAt;} public String getReason(){return reason;} public LocalDateTime getStartedAt(){return startedAt;} public String getFailureCode(){return failureCode;}
 public boolean canRetry(LocalDateTime nowUtc){
  return status==RefundStatus.REQUESTED || status==RefundStatus.FAILED
      || (status==RefundStatus.PROCESSING && startedAt!=null && startedAt.isBefore(nowUtc.minusSeconds(30)));
 }
 public void start(LocalDateTime nowUtc){
  if(!canRetry(nowUtc))
   throw new IllegalStateException("Refund is not ready for processing");
  status=RefundStatus.PROCESSING;startedAt=nowUtc;failureCode=null;
 }
 public void refunded(LocalDateTime nowUtc){
  refunded(nowUtc,null);
 }
 public void refunded(LocalDateTime nowUtc,UserEntity actor){
  if(status!=RefundStatus.PROCESSING)throw new IllegalStateException("Refund is not processing");
  status=RefundStatus.REFUNDED;processedBy=actor;processedAt=nowUtc;failureCode=null;
 }
 public void failed(String code){
  if(status!=RefundStatus.PROCESSING && status!=RefundStatus.REQUESTED)
   throw new IllegalStateException("Refund is not processing");
  status=RefundStatus.FAILED;failureCode=code;
 }
 public void process(UserEntity admin,RefundStatus status,LocalDateTime at){if(this.status!=RefundStatus.REQUESTED)throw new IllegalStateException("Refund already processed");if(status!=RefundStatus.APPROVED && status!=RefundStatus.REJECTED)throw new IllegalArgumentException("Refund decision must be approved or rejected");this.status=status;processedBy=admin;processedAt=at;}
}
