package app.netlify.nmhillusion.raccoon_scheduler.service_impl.world_cup_stat;

import tech.nmhillusion.n2mix.util.StringUtil;
import tech.nmhillusion.n2mix.validator.StringValidator;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * date: 2022-11-27
 * <p>
 * created-by: nmhillusion
 */
class MatchParser {
	public List<String> obtainMainStat(String pageContent, List<String> idWorldCupStatElList) {
		final List<String> resultList = new ArrayList<>();

		for (String idWorldCupStatEl : idWorldCupStatElList) {
			final Pattern mainStatContentPattern = Pattern.compile("<table\\s+.*?id=['\"]" + idWorldCupStatEl + "[\"'].*?>(.+?)</table>", Pattern.CASE_INSENSITIVE);
			final Matcher matcher = mainStatContentPattern.matcher(pageContent);
			if (matcher.find()) {
				resultList.add(matcher.group(1));
			}
		}

		return resultList;
	}

	public List<String> obtainForEachMatch(List<String> mainStatContentList) {
		final List<String> matchesResult = new ArrayList<>();

		for (String mainStatContent : mainStatContentList) {
			final Pattern eachMatchPattern = Pattern.compile("<tr\\s*><th\\s*scope=[\"']row[\"'].*?data-stat=['\"](?:gameweek|round)['\"].*?>(.*?)</tr>", Pattern.CASE_INSENSITIVE);
			final Matcher matcher = eachMatchPattern.matcher(mainStatContent);

			while (matcher.find()) {
				matchesResult.add(
						matcher.group(1)
				);
			}
		}

		return matchesResult;
	}

	public List<String> parseEachRowToCellList(String matchContent) {
		final List<String> result = new ArrayList<>();

		final Pattern eachCellPattern = Pattern.compile("<td.*?>.*?</td>", Pattern.CASE_INSENSITIVE);
		final Matcher matcher = eachCellPattern.matcher(matchContent);

		while (matcher.find()) {
			result.add(
					matcher.group(0)
			);
		}

		return result;
	}

	public String parseDateFromCells(@NotNull List<String> cells) {
		String result = "";
		final Pattern pattern_ = Pattern.compile("<td\\s*.*?data-stat=['\"]date['\"].*?>(.*?)</td>", Pattern.CASE_INSENSITIVE);
		for (String cell : cells) {
			final Matcher matcher = pattern_.matcher(cell);
			if (matcher.find()) {
				result = matcher.group(1);
				result = StringUtil.removeHtmlTag(result);
			}
		}
		return result;
	}

	public ZonedDateTime parseStartTimeFromCells(@NotNull List<String> cells) {
		ZonedDateTime result = null;
		final Pattern pattern_ = Pattern.compile("<td\\s*.*?data-stat=['\"]start_time['\"].*?>(.*?)</td>", Pattern.CASE_INSENSITIVE);
		for (String cell : cells) {
			final Matcher matcher = pattern_.matcher(cell);
			if (matcher.find()) {
				String rawResult = matcher.group(1);

				final Pattern venueEpochPattern = Pattern.compile("<span\\s*.*?data-venue-epoch=['\"](\\d+)['\"].*?>", Pattern.CASE_INSENSITIVE);
				final Matcher venueEpochMatcher = venueEpochPattern.matcher(rawResult);

				if (venueEpochMatcher.find()) {
					long timeInSecondFromEpoch = Long.parseLong(venueEpochMatcher.group(1));
					final Instant instant = Instant.ofEpochSecond(timeInSecondFromEpoch);
					result = ZonedDateTime.ofInstant(instant, ZoneOffset.UTC);
				}
			}
		}
		return result;
	}

	private String removeSpanContent(String content) {
		return content.replaceAll("<span.*?>.*?</span>", "");
	}

	public String parseHomeTeamFromCells(@NotNull List<String> cells) {
		String result = "";
		final Pattern pattern_ = Pattern.compile("<td\\s*.*?data-stat=['\"]home_team['\"].*?>(.*?)</td>", Pattern.CASE_INSENSITIVE);
		for (String cell : cells) {
			final Matcher matcher = pattern_.matcher(cell);
			if (matcher.find()) {
				result = matcher.group(1);
				result = StringUtil.trimWithNull(StringUtil.removeHtmlTag(removeSpanContent(result)));
			}
		}
		return result;
	}

	public String parseHomeTeamShortNameFromCells(List<String> cells) {
		// <span class="f-i f-nl" style="">nl</span>
		String result = "";
		final Pattern pattern_ = Pattern.compile("<td\\s*.*?data-stat=['\"]home_team['\"].*?>.*?<span class=\"f-i f-(.+?)\" style=\"\">.*?</span>.*?</td>", Pattern.CASE_INSENSITIVE);
		for (String cell : cells) {
			final Matcher matcher = pattern_.matcher(cell);
			if (matcher.find()) {
				result = matcher.group(1);
				result = StringUtil.trimWithNull(StringUtil.removeHtmlTag(removeSpanContent(result)));
			}
		}
		return result;
	}

	public String parseScoreFromCells(@NotNull List<String> cells) {
		String result = "";
		final Pattern pattern_ = Pattern.compile("<td\\s*.*?data-stat=['\"]score['\"].*?>(.*?)</td>", Pattern.CASE_INSENSITIVE);
		for (String cell : cells) {
			final Matcher matcher = pattern_.matcher(cell);
			if (matcher.find()) {
				result = matcher.group(1);
				result = HtmlUtils.htmlUnescape(StringUtil.removeHtmlTag(result));
			}
		}
		return result;
	}

	private List<Integer> parseScoreItemsFromPureScore(String pureScore) {
		final Pattern scorePattern = Pattern.compile("(?:\\((\\d+)\\))?\\s*(\\d+)\\s*\\D+\\s*(\\d+)\\s*(?:\\((\\d+)\\))?");
		final Matcher matcher = scorePattern.matcher(pureScore);
		final List<Integer> resultList = Arrays.asList(-1, -1, -1, -1);
		if (matcher.find()) {
			for (int groupNum = 1; groupNum <= 4; ++groupNum) {
				final String group_ = matcher.group(groupNum);
				if (!StringValidator.isBlank(group_)) {
					resultList.set(groupNum - 1, Integer.parseInt(group_));
				}
			}
		}

		return resultList;
	}

	public int parseHomeTeamScoreFromScore(String score) {
		final List<Integer> scoreItems = parseScoreItemsFromPureScore(score);
		return scoreItems.get(1);
	}

	public int parseHomeTeamPenaltyScoreFromScore(String score) {
		final List<Integer> scoreItems = parseScoreItemsFromPureScore(score);
		return scoreItems.get(0);
	}

	public int parseAwayTeamScoreFromScore(String score) {
		final List<Integer> scoreItems = parseScoreItemsFromPureScore(score);
		return scoreItems.get(2);
	}

	public int parseAwayTeamPenaltyScoreFromScore(String score) {
		final List<Integer> scoreItems = parseScoreItemsFromPureScore(score);
		return scoreItems.get(3);
	}

	public String parseAwayTeamFromCells(@NotNull List<String> cells) {
		String result = "";
		final Pattern pattern_ = Pattern.compile("<td\\s*.*?data-stat=['\"]away_team['\"].*?>(.*?)</td>", Pattern.CASE_INSENSITIVE);
		for (String cell : cells) {
			final Matcher matcher = pattern_.matcher(cell);
			if (matcher.find()) {
				result = matcher.group(1);
				result = StringUtil.trimWithNull(StringUtil.removeHtmlTag(removeSpanContent(result)));
			}
		}
		return result;
	}

	public String parseAwayTeamShortNameFromCells(List<String> cells) {
		// <span class="f-i f-nl" style="">nl</span>
		String result = "";
		final Pattern pattern_ = Pattern.compile("<td\\s*.*?data-stat=['\"]away_team['\"].*?>.*?<span class=\"f-i f-(.+?)\" style=\"\">.*?</span>.*?</td>", Pattern.CASE_INSENSITIVE);
		for (String cell : cells) {
			final Matcher matcher = pattern_.matcher(cell);
			if (matcher.find()) {
				result = matcher.group(1);
				result = StringUtil.trimWithNull(StringUtil.removeHtmlTag(removeSpanContent(result)));
			}
		}
		return result;
	}

	public int parseAttendanceFromCells(@NotNull List<String> cells) {
		int result = 0;
		final Pattern pattern_ = Pattern.compile("<td\\s*.*?data-stat=['\"]attendance['\"].*?>(.*?)</td>", Pattern.CASE_INSENSITIVE);
		for (String cell : cells) {
			final Matcher matcher = pattern_.matcher(cell);
			if (matcher.find()) {
				String rawResult = matcher.group(1);
				rawResult = StringUtil.removeHtmlTag(rawResult).replaceAll("\\D", "");

				if (!StringValidator.isBlank(rawResult)) {
					result = Integer.parseInt(rawResult);
				}
			}
		}
		return result;
	}

	public String parseVenueFromCells(@NotNull List<String> cells) {
		String result = "";
		final Pattern pattern_ = Pattern.compile("<td\\s*.*?data-stat=['\"]venue['\"].*?>(.*?)</td>", Pattern.CASE_INSENSITIVE);
		for (String cell : cells) {
			final Matcher matcher = pattern_.matcher(cell);
			if (matcher.find()) {
				result = matcher.group(1);
				result = StringUtil.removeHtmlTag(result);
			}
		}
		return result;
	}

	public String parseRefereeFromCells(@NotNull List<String> cells) {
		String result = "";
		final Pattern pattern_ = Pattern.compile("<td\\s*.*?data-stat=['\"]referee['\"].*?>(.*?)</td>", Pattern.CASE_INSENSITIVE);
		for (String cell : cells) {
			final Matcher matcher = pattern_.matcher(cell);
			if (matcher.find()) {
				result = matcher.group(1);
				result = StringUtil.removeHtmlTag(result);
			}
		}
		return result;
	}
}
