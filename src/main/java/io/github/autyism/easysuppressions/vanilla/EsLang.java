package io.github.autyism.easysuppressions.vanilla;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.autyism.easysuppressions.EasySuppressions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The mod's texts on the server, for clients without the mod: they have no translations for its
 * keys. The same language files as the client's; Chinese clients get zh_cn, everybody else en_us.
 */
public final class EsLang {
	private static final Map<String, Map<String, String>> LANGUAGES = new HashMap<>();

	private EsLang() {
	}

	private static Map<String, String> table(String language) {
		String file = language != null && language.toLowerCase().startsWith("zh") ? "zh_cn" : "en_us";
		return LANGUAGES.computeIfAbsent(file, EsLang::load);
	}

	private static Map<String, String> load(String file) {
		Map<String, String> map = new HashMap<>();
		try (InputStream in = EsLang.class.getResourceAsStream("/assets/easysuppressions/lang/" + file + ".json")) {
			if (in != null) {
				JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				json.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
			}
		} catch (Exception e) {
			EasySuppressions.LOGGER.warn("Could not read the {} language file", file, e);
		}
		return map;
	}

	/** One text in the player's language, formatted like the game does (%s, %1$s). */
	public static String get(ServerPlayerEntity player, String key, Object... args) {
		return get(EsClients.language(player), key, args);
	}

	public static String get(String language, String key, Object... args) {
		String pattern = table(language).getOrDefault(key, table("en_us").getOrDefault(key, key));
		Object[] plain = new Object[args.length];
		for (int i = 0; i < args.length; i++) {
			plain[i] = args[i] instanceof Text t ? translate(t, language).getString() : args[i];
		}
		try {
			return args.length == 0 ? pattern : String.format(pattern, plain);
		} catch (RuntimeException e) {
			return pattern;
		}
	}

	public static Text text(ServerPlayerEntity player, String key, Object... args) {
		return Text.literal(get(player, key, args));
	}

	/** Does the text use one of the mod's keys anywhere? */
	public static boolean mentions(Text text) {
		if (text.getContent() instanceof TranslatableTextContent t) {
			if (isOurs(t.getKey())) {
				return true;
			}
			for (Object arg : t.getArgs()) {
				if (arg instanceof Text a && mentions(a)) {
					return true;
				}
			}
		}
		for (Text sibling : text.getSiblings()) {
			if (mentions(sibling)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isOurs(String key) {
		return key.startsWith("easysuppressions.") || key.contains(".easysuppressions.") || key.endsWith(".easysuppressions");
	}

	/**
	 * The text with the mod's keys given a fallback in the player's language: a client without the
	 * mod shows the fallback, and still fills in the arguments itself. Styles stay as they are.
	 */
	public static Text translate(Text text, String language) {
		if (!mentions(text)) {
			return text;
		}
		TextContent content = text.getContent();
		MutableText out;
		if (content instanceof TranslatableTextContent t) {
			Object[] args = new Object[t.getArgs().length];
			for (int i = 0; i < args.length; i++) {
				args[i] = t.getArgs()[i] instanceof Text a ? translate(a, language) : t.getArgs()[i];
			}
			String fallback = isOurs(t.getKey()) ? table(language).getOrDefault(t.getKey(), table("en_us").get(t.getKey())) : t.getFallback();
			out = Text.translatableWithFallback(t.getKey(), fallback, args);
		} else {
			out = MutableText.of(content);
		}
		out.setStyle(text.getStyle());
		for (Text sibling : text.getSiblings()) {
			out.append(translate(sibling, language));
		}
		return out;
	}
}
