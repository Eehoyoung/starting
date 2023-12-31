package com.growable.starting.service;

import com.growable.starting.dto.LectureDto;
import com.growable.starting.exception.InsufficientFundsException;
import com.growable.starting.exception.MentorNotFoundException;
import com.growable.starting.exception.NotFoundException;
import com.growable.starting.model.Enrollment;
import com.growable.starting.model.Lecture;
import com.growable.starting.model.Mentee;
import com.growable.starting.model.Mentor;
import com.growable.starting.model.type.LectureStatus;
import com.growable.starting.repository.EnrollmentRepository;
import com.growable.starting.repository.LectureRepository;
import com.growable.starting.repository.MenteeRepository;
import com.growable.starting.repository.MentorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDate;
import java.util.List;

@Service
@Slf4j
public class LectureServiceImpl implements LectureService {

    private final LectureRepository lectureRepository;
    private final MentorRepository mentorRepository;
    private final MenteeRepository menteeRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EmailService emailService;

    @Autowired
    public LectureServiceImpl(LectureRepository lectureRepository, MentorRepository mentorRepository, MenteeRepository menteeRepository, EnrollmentRepository enrollmentRepository, EmailService emailService) {
        this.lectureRepository = lectureRepository;
        this.mentorRepository = mentorRepository;
        this.menteeRepository = menteeRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.emailService = emailService;
    }


    @Override
    @Transactional
    public Lecture createLecture(Long mentorId, LectureDto lectureDto) {
        Mentor mentor = mentorRepository.findById(mentorId)
                .orElseThrow(() -> new MentorNotFoundException("Mentor not found with ID: " + mentorId));

        Lecture lecture = new Lecture();
        lecture.setTitle(lectureDto.getTitle());
        lecture.setRecruitmentStartDate(lectureDto.getRecruitmentStartDate());
        lecture.setRecruitmentEndDate(lectureDto.getRecruitmentEndDate());
        lecture.setCapacity(lectureDto.getCapacity());
        lecture.setFee(lectureDto.getFee());
        lecture.setLectureStartDate(lectureDto.getLectureStartDate());
        lecture.setLectureEndDate(lectureDto.getLectureEndDate());
        lecture.setMentorName(mentor.getName());
        lecture.setStatus(LectureStatus.NOT_STARTED);
        lecture.setMentor(mentor);
        lecture.setTeamUrl(lectureDto.getTeamUrl());

        return lectureRepository.save(lecture);
    }

//  30초마다 강의 모집 정보 update
@Scheduled(cron = "*/30 * * * * *")
public void updateLectureStatus() {
    // 모든 강의를 가져옵니다.
    List<Lecture> lectures = lectureRepository.findAll();

    // 현재 시간 가져오기
    LocalDateTime currentTime = LocalDateTime.now();

    // 각 강의에 대해 상태를 확인하고 업데이트합니다.
    for (Lecture lecture : lectures) {
        // 현재 강의를 신청한 학생 수를 가져옵니다.
            int enrolledStudents = lectureRepository.countEnrolledStudentsForCurrentLecture(lecture.getId());

            // 모집일이 이미 종료된 경우
            if (lecture.getRecruitmentEndDate().isBefore(ChronoLocalDate.from(currentTime))) {
                lecture.setStatus(LectureStatus.RECRUITMENT_ENDED);
            } else if (lecture.getRecruitmentStartDate().isBefore(ChronoLocalDate.from(currentTime)) &&
                    lecture.getRecruitmentEndDate().isAfter(ChronoLocalDate.from(currentTime))) {

                // 정원 초과 확인
                boolean isCapacityExceeded = lecture.getCapacity() <= enrolledStudents;

                // 정원 초과이거나 정원이 미달인 경우
                if (isCapacityExceeded) {
                    lecture.setStatus(LectureStatus.RECRUITMENT_ENDED);
                } else {
                    lecture.setStatus(LectureStatus.RECRUITING);
                }
            } else {
                lecture.setStatus(LectureStatus.NOT_STARTED);
            }
            // 상태가 변경된 강의를 저장합니다.
            lectureRepository.save(lecture);
        }
    }

    //  mentee 강의 신청 -> 결제 -> 완료 후 이메일로 신청 내역 전달
    @Override
    @Transactional
    public Enrollment enrollInLecture(Long menteeId, Long lectureId) {
        Mentee mentee = menteeRepository.findById(menteeId).orElseThrow(() -> new NotFoundException("Mentee not found"));
        Lecture lecture = lectureRepository.findById(lectureId).orElseThrow(() -> new NotFoundException("Lecture not found"));
        Mentor mentor = lecture.getMentor();
        Enrollment enrollment = new Enrollment(mentee, lecture, mentor);
        enrollmentRepository.save(enrollment);
        if (mentee.getPoint() < lecture.getFee()) {
            throw new InsufficientFundsException("Not enough points of mentee '" + mentee.getName() + "' to enroll in lecture '" + lecture.getTitle() + "'");
        } else {
            mentee.setPoint(mentee.getPoint() - lecture.getFee());
            menteeRepository.save(mentee);
            String emailAddress = mentee.getEmail();
            String subject = mentee.getName() + "님 " + lecture.getTitle() + "의 신청이 완료되었습니다.";
            String text = lecture.getLectureStartDate().getMonth() + "월 " +
                    lecture.getLectureStartDate().getDayOfMonth() + "일에 " +
                    lecture.getTeamUrl() + " 로 접속해주세요.";

            emailService.sendEnrollmentConfirmationEmail(emailAddress, subject, text);
        }
        return enrollment;
    }


    @Override
    @Transactional
    public Enrollment cancelLectureEnrollment(Long menteeId, Long lectureId) {
        Mentee mentee = menteeRepository.findById(menteeId).orElseThrow(() -> new NotFoundException("Mentee not found"));
        Lecture lecture = lectureRepository.findById(lectureId).orElseThrow(() -> new NotFoundException("Lecture not found"));

        Enrollment enrollment = enrollmentRepository.findByMenteeAndLecture(mentee, lecture).orElseThrow(() ->
                new NotFoundException("Enrollment not found"));

        enrollmentRepository.delete(enrollment);
        return enrollment;
    }

    @Override
    public List<Lecture> findAllLectures() {
        return lectureRepository.findAll();
    }
}

