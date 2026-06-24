package com.example.emailsender.tracking;

import com.example.emailsender.send.SentMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrackingEventRepository extends JpaRepository<TrackingEvent, Long> {

    List<TrackingEvent> findTop10BySentMessageOrderByLoadedAtDesc(SentMessage sentMessage);
}
