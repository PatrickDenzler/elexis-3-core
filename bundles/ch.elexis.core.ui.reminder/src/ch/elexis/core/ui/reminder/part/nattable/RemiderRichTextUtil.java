package ch.elexis.core.ui.reminder.part.nattable;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import ch.elexis.core.model.IPerson;
import ch.elexis.core.model.IReminder;
import ch.elexis.core.model.issue.Priority;

public class RemiderRichTextUtil {

	private static Cache<String, IReminder> cache = CacheBuilder.newBuilder().expireAfterAccess(15, TimeUnit.SECONDS)
			.build();

	public static String richText(IReminder reminder, boolean addDate) {
		StringBuilder sb = new StringBuilder();
		if (reminder.getPriority() == Priority.HIGH) {
			sb.append("<strong><span style=\"font-size: 14; color:rgb(255,0,0);\">");
			sb.append(" !! ");
			sb.append("</span></strong>");
		}
		sb.append("<strong>" + getSubject(reminder) + "</strong>").append("<br />");
		IReminder resolved = cache.getIfPresent(reminder.getId());
		if (resolved != null) {
			if (resolved.getContact() != null && resolved.getContact().isPatient()) {
				sb.append(getPersonalia(resolved.getContact().asIPerson()));
			}
		} else {
			sb.append("...");
			CompletableFuture.runAsync(() -> {
				reminder.getContact();
				cache.put(reminder.getId(), reminder);
			});
		}
		sb.append("<addition>");
		if (addDate && reminder.getDue() != null) {
			sb.append(DateTimeFormatter.ofPattern("dd.MM").format(reminder.getDue()));
		}
		sb.append("</addition>");
		return sb.toString();
	}

	private static String getPersonalia(IPerson person) {
		if (person != null) {
			StringBuilder sb = new StringBuilder(64);
			if (StringUtils.isNoneEmpty(person.getLastName())) {
				sb.append(person.getLastName());
			}
			if (StringUtils.isNotBlank(sb.toString())) {
				sb.append(StringUtils.SPACE);
			}
			if (StringUtils.isNoneEmpty(person.getFirstName())) {
				sb.append(person.getFirstName());
			}
			if (person.getDateOfBirth() != null) {
				if (StringUtils.isNotBlank(sb.toString())) {
					sb.append(StringUtils.SPACE);
				}
				sb.append(DateTimeFormatter.ofPattern("dd.MM.yy").format(person.getDateOfBirth()));
			}
			return sb.toString();
		}
		return StringUtils.EMPTY;
	}

	private static String getSubject(IReminder reminder) {
		if (StringUtils.isNotBlank(reminder.getSubject())) {
			return reminder.getSubject();
		} else if (StringUtils.isNotBlank(reminder.getMessage())) {
			return StringUtils.abbreviate(reminder.getMessage(), 80);
		}
		return StringUtils.EMPTY;
	}

	public static String richText(String string) {
		StringBuilder sb = new StringBuilder();
		sb.append("<strong>" + string + "</strong>");
		return sb.toString();
	}

	public static String richText(String string, int increaseFontSize) {
		StringBuilder sb = new StringBuilder();
		int size = 14 + increaseFontSize;
		sb.append("<p style=\"width: 100%;text-align: center\">");
		sb.append("<span style=\"font-size: " + size + "\">");
		sb.append("<strong>" + string + "</strong>");
		sb.append("</span>");
		sb.append("</p>");
		return sb.toString();
	}

	public static void invalidateCache(IReminder reminder) {
		cache.invalidate(reminder.getId());
	}
}
