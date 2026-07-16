package com.example.dossia.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatQueryMatcherTest {

    private ChatQueryMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new ChatQueryMatcher();
    }

    @Test
    void detectsArabiziPassportIntent() {
        assertTrue(matcher.isDocumentAsk("chnoua el awra9 lil paspor"));
        assertFalse(matcher.matchIntentSlugs("paspor biometrique").isEmpty());
    }

    @Test
    void detectsDerjaLocationIntent() {
        assertTrue(matcher.isLocationIntent("win nemchi pour le passeport?"));
        assertTrue(matcher.isLocationIntent("وين نمشي pour CIN"));
        assertFalse(matcher.isLocationIntent("I want to win a lottery ticket"));
    }

    @Test
    void detectsChecklistAndDocumentAsks() {
        assertTrue(matcher.isChecklistAsk("Quels documents pour un passeport ?"));
        assertTrue(matcher.isChecklistAsk("aamel liste des papiers"));
        assertTrue(matcher.isDocumentAsk("awra9 el jawaz"));
    }

    @Test
    void followUpStayInThread() {
        assertTrue(matcher.isFollowUp("1"));
        assertTrue(matcher.isFollowUp("ey"));
        assertTrue(matcher.isFollowUp("behi"));
        assertTrue(matcher.isThreadContinuation("ok"));
        assertTrue(matcher.isThreadContinuation("1"));
    }

    @Test
    void greetingVsLowSignal() {
        assertTrue(matcher.isGreeting("salam"));
        assertTrue(matcher.isGreeting("ahlan"));
        assertTrue(matcher.isLowSignalQuery("cv"));
        assertTrue(matcher.isLowSignalQuery("??"));
        assertFalse(matcher.isLowSignalQuery("paspor tunisien"));
    }

    @Test
    void detectsArabicScriptPassportIntent() {
        assertFalse(matcher.matchIntentSlugs("حبيبه ديال الباسبور في تونس").isEmpty());
        assertFalse(matcher.matchIntentSlugs("نبدل الباسبور").isEmpty());
        assertTrue(matcher.extractSearchTerms("الباسبور في تونس").contains("passeport"));
    }

    @Test
    void detectsAsrStyleNationalIdIntent() {
        // Chrome often writes ة as ه: بطاقه التعريف
        String asr = "نحب الطريف نحب نبدل بطاقه التعريف";
        assertFalse(matcher.matchIntentSlugs(asr).isEmpty());
        assertTrue(matcher.extractSearchTerms(asr).contains("identite"));
    }

    @Test
    void passportBeatsCinWhenBothRenewWordsPresent() {
        String q = "على السلامه نحب نجدد الباسبور";
        assertEquals(ChatQueryMatcher.TopicHint.PASSPORT, matcher.detectTopicHint(q));
        assertTrue(
                matcher.matchIntentSlugs(q).stream().anyMatch(s -> s.contains("passeport")),
                "expected passport slug");
        assertTrue(
                matcher.matchIntentSlugs(q).stream().noneMatch(s -> s.contains("identite") || s.contains("cin")),
                "CIN should be dropped when باسبور is explicit");
    }

    @Test
    void arabicDocumentCue() {
        assertTrue(matcher.isDocumentAsk("ما هي الوثائق المطلوبة"));
        assertTrue(matcher.isDocumentAsk("chnoua el awrak"));
    }
}
