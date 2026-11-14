package com.b.now._29.app.quiz.ranking;

/** 랭킹 한 줄. 이메일과 회원 id는 담지 않는다 (R6, D7). */
public record RankingRow(long rank, String nickname, int tag, long score) {}
