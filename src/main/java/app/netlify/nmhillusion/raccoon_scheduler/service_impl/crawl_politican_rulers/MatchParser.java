package app.netlify.nmhillusion.raccoon_scheduler.service_impl.crawl_politican_rulers;

import app.netlify.nmhillusion.n2mix.helper.http.RequestHttpBuilder;
import app.netlify.nmhillusion.n2mix.util.DateUtil;
import app.netlify.nmhillusion.n2mix.util.RegexUtil;
import app.netlify.nmhillusion.n2mix.util.StringUtil;
import app.netlify.nmhillusion.n2mix.validator.StringValidator;
import app.netlify.nmhillusion.raccoon_scheduler.entity.politics_rulers.IndexEntity;
import app.netlify.nmhillusion.raccoon_scheduler.entity.politics_rulers.PoliticianEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static app.netlify.nmhillusion.n2mix.helper.log.LogHelper.getLog;

/**
 * date: 2022-12-29
 * <p>
 * created-by: nmhillusion
 */
class MatchParser {

	private String buildDatePatternOfPrefix(String prefix) {
		return prefix + "\\.\\s*(?:([a-z]{3,6})\\.?\\??)?\\s*(?:(\\d+)\\??,)?\\s*(\\d{4})\\??";
	}

	private String buildPatternOfPlaceOfBirth() {
		return buildDatePatternOfPrefix("b") + "(.*?)\\s*(?:-\\s*d\\.(.*?))?$";
	}

	private String buildPatternOfPlaceOfDeath() {
		return "-\\s*" + buildDatePatternOfPrefix("d") + "(.+?)$";
	}

	private LocalDate parseDateOfBirthPhrase(String phrase) {
		LocalDate localDate = null;

		final List<List<String>> parsedList = RegexUtil.parse(phrase, buildDatePatternOfPrefix("b"), Pattern.CASE_INSENSITIVE);
		if (!parsedList.isEmpty()) {
			final List<String> parsed = parsedList.get(0);
			localDate = DateUtil.buildDateFromString(parsed.get(2), parsed.get(1), parsed.get(3));
		}

		return localDate;
	}

	private LocalDate parseDateOfDeathPhrase(String phrase) {
		LocalDate localDate = null;

		final List<List<String>> parsedList = RegexUtil.parse(phrase, buildDatePatternOfPrefix("d"), Pattern.CASE_INSENSITIVE);
		if (!parsedList.isEmpty()) {
			final List<String> parsed = parsedList.get(0);
			localDate = DateUtil.buildDateFromString(parsed.get(2), parsed.get(1), parsed.get(3));
		}

		return localDate;
	}

	private String parsePlaceOfLifetime(String lifetime, boolean isBirth) {
		String place = "";

		final List<List<String>> parsedList = RegexUtil.parse(lifetime, isBirth ? buildPatternOfPlaceOfBirth() : buildPatternOfPlaceOfDeath(), Pattern.CASE_INSENSITIVE);
		if (!parsedList.isEmpty()) {
			final List<String> parsed = parsedList.get(0);
			place = StringUtil.trimWithNull(parsed.get(4));

			while (place.matches("^\\W(?![\\[\\]])(.+?)") && 1 < place.length()) {
				place = StringUtil.trimWithNull(place.substring(1));
			}
			place = StringUtil.trimWithNull(place);

			while (place.matches("\\W(?![\\[\\]])$") && 1 < place.length()) {
				place = StringUtil.trimWithNull(place.substring(0, place.length() - 1));
			}
			place = StringUtil.trimWithNull(place);
		}

		return place;
	}

	private String parsePlaceOfBirth(String lifetime) {
		return parsePlaceOfLifetime(lifetime, true);
	}

	private String parsePlaceOfDeath(String lifetime) {
		return parsePlaceOfLifetime(lifetime, false);
	}

	private Optional<PoliticianEntity> parseCharacterParagraph(String paragraph) {
		paragraph = StringUtil.trimWithNull(paragraph);
		final List<List<String>> parsedList = RegexUtil.parse(paragraph, "(.+?)<\\/b>\\s*\\((.*?)\\),(.*?)\\.(?:(.+?)\\.)?<p>.*?", Pattern.CASE_INSENSITIVE);

		Optional<PoliticianEntity> result = Optional.empty();

		final Optional<List<String>> firstParsed = parsedList.stream().findFirst();
		if (firstParsed.isPresent()) {
			final String originalParagraph = HtmlUtils.htmlUnescape(
					StringUtil.trimWithNull(
							firstParsed.get().get(0)
					)
			);

			final String fullName = HtmlUtils.htmlUnescape(
					StringUtil.trimWithNull(
							firstParsed.get().get(1)
					)
			);
			final String lifeTime = HtmlUtils.htmlUnescape(
					StringUtil.trimWithNull(
							firstParsed.get().get(2)
					)
			);
			final String role = HtmlUtils.htmlUnescape(
					StringUtil.trimWithNull(
							firstParsed.get().get(3)
					)
			);
			final String note = HtmlUtils.htmlUnescape(
					StringUtil.trimWithNull(
							firstParsed.get().get(4)
					)
			);

			result = Optional.of(new PoliticianEntity()
					.setOriginalParagraph(originalParagraph)
					.setFullName(fullName)
					.setPrimaryName(parsePrimaryNameFromFullName(fullName))
					.setSecondaryName(parseSecondaryNameFromFullName(fullName))
					.setDateOfBirth(parseDateOfBirthPhrase(lifeTime))
					.setDateOfDeath(parseDateOfDeathPhrase(lifeTime))
					.setPlaceOfBirth(StringUtil.removeHtmlTag(parsePlaceOfBirth(lifeTime)))
					.setPlaceOfDeath(StringUtil.removeHtmlTag(parsePlaceOfDeath(lifeTime)))
					.setPosition(StringUtil.removeHtmlTag(role))
					.setNote(StringUtil.removeHtmlTag(note))
			);
		}

		return result;
	}

	private String parsePrimaryNameFromFullName(String fullName) {
		if (StringValidator.isBlank(fullName)) {
			return fullName;
		}
		return fullName.replaceAll("\\(.*?\\)", "")
				.replaceAll(" {2,}", " ");
	}

	private String parseSecondaryNameFromFullName(String fullName) {
		if (StringValidator.isBlank(fullName)) {
			return fullName;
		}
		final Pattern secondaryPattern = Pattern.compile("\\((.*?)\\)", Pattern.DOTALL);
		final Matcher matched = secondaryPattern.matcher(fullName);

		final List<String> fragList = new ArrayList<>();
		while (matched.find()) {
			fragList.add(
					matched.group(1)
			);
		}
		return String.join(";", fragList);
	}

	public List<PoliticianEntity> parseCharacterPage(String pageContent) throws Exception {
		final List<PoliticianEntity> resultList = new ArrayList<>();

		if (StringValidator.isBlank(pageContent)) {
			throw new IOException("Page Content is empty");
		}

		final String[] splitCharacter = pageContent.split("(?i)<b>");

		getLog(this).info("splitCharacter: " + splitCharacter.length);

		if (1 >= splitCharacter.length) {
			throw new Exception("Structure of character page is not valid");
		}

		final List<String> characterParagraphs = Arrays.asList(splitCharacter).subList(1, splitCharacter.length);

		for (String characterParagraph : characterParagraphs) {
			final Optional<PoliticianEntity> politicianEntity = parseCharacterParagraph(characterParagraph);
			politicianEntity.ifPresent(resultList::add);
		}

		return resultList;
	}
}
