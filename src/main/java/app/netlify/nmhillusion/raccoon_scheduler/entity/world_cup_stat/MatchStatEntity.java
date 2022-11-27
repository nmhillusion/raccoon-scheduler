package app.netlify.nmhillusion.raccoon_scheduler.entity.world_cup_stat;

import app.netlify.nmhillusion.n2mix.type.ChainMap;
import app.netlify.nmhillusion.n2mix.type.Stringeable;

import java.time.ZonedDateTime;
import java.util.Map;

/**
 * date: 2022-11-27
 * <p>
 * created-by: nmhillusion
 */

public class MatchStatEntity extends Stringeable {
    private String matchId;
    private String date;
    private ZonedDateTime startTime;
    private String homeTeam;
    private String score;
    private String awayTeam;
    private int attendance;
    private String venue;
    private String referee;
    private int homeTeamScore;
    private int awayTeamScore;

    public String getMatchId() {
        return matchId;
    }

    public MatchStatEntity setMatchId(String matchId) {
        this.matchId = matchId;
        return this;
    }

    public String getDate() {
        return date;
    }

    public MatchStatEntity setDate(String date) {
        this.date = date;
        return this;
    }

    public ZonedDateTime getStartTime() {
        return startTime;
    }

    public MatchStatEntity setStartTime(ZonedDateTime startTime) {
        this.startTime = startTime;
        return this;
    }

    public String getHomeTeam() {
        return homeTeam;
    }

    public MatchStatEntity setHomeTeam(String homeTeam) {
        this.homeTeam = homeTeam;
        return this;
    }

    public String getScore() {
        return score;
    }

    public MatchStatEntity setScore(String score) {
        this.score = score;
        return this;
    }

    public String getAwayTeam() {
        return awayTeam;
    }

    public MatchStatEntity setAwayTeam(String awayTeam) {
        this.awayTeam = awayTeam;
        return this;
    }

    public int getAttendance() {
        return attendance;
    }

    public MatchStatEntity setAttendance(int attendance) {
        this.attendance = attendance;
        return this;
    }

    public String getVenue() {
        return venue;
    }

    public MatchStatEntity setVenue(String venue) {
        this.venue = venue;
        return this;
    }

    public String getReferee() {
        return referee;
    }

    public MatchStatEntity setReferee(String referee) {
        this.referee = referee;
        return this;
    }

    public int getHomeTeamScore() {
        return homeTeamScore;
    }

    public MatchStatEntity setHomeTeamScore(int homeTeamScore) {
        this.homeTeamScore = homeTeamScore;
        return this;
    }

    public int getAwayTeamScore() {
        return awayTeamScore;
    }

    public MatchStatEntity setAwayTeamScore(int awayTeamScore) {
        this.awayTeamScore = awayTeamScore;
        return this;
    }

    public Map<String, Object> toMap() {
        return new ChainMap<String, Object>()
                .chainPut("matchId", matchId)
                .chainPut("date", date)
                .chainPut("startTime", startTime.toString())
                .chainPut("homeTeam", homeTeam)
                .chainPut("score", score)
                .chainPut("awayTeam", awayTeam)
                .chainPut("attendance", attendance)
                .chainPut("venue", venue)
                .chainPut("referee", referee)
                .chainPut("homeTeamScore", homeTeamScore)
                .chainPut("awayTeamScore", awayTeamScore)
                ;
    }
}
