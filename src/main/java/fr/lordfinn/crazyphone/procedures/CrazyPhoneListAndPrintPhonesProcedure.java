package fr.lordfinn.crazyphone.procedures;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.ClickEvent;

import java.net.*;
import java.io.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;

import net.minecraft.nbt.StringTag;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;

public class CrazyPhoneListAndPrintPhonesProcedure {
	private static final Map<String, String> uuidToNameCache = new ConcurrentHashMap<>();

	public static void execute(LevelAccessor world, CommandContext<CommandSourceStack> arguments, Entity entity) {
		if (entity == null || !(entity instanceof ServerPlayer))
			return;
		String search = "";
		try {
			search = StringArgumentType.getString(arguments, "search");
		} catch (Exception e) {
		}

		ServerPlayer player = (ServerPlayer) entity;
		player.sendSystemMessage(Component.literal("--------------------------------------------"));
		for (String phoneKey : PhoneRegistrySavedData.get(world).phones.getAllKeys()) {
			CompoundTag phone = (PhoneRegistrySavedData.get(world).phones
					.get(phoneKey)) instanceof CompoundTag _compoundTag
							? _compoundTag.copy()
							: new CompoundTag();

			String password = phone.get("password") instanceof StringTag s1 ? s1.getAsString() : "";
			String uuid = phone.get("uuid") instanceof StringTag s2 ? s2.getAsString() : "";
			String name = phone.get("name") instanceof StringTag s3 ? s3.getAsString() : "";
			String skin = phone.get("skin") instanceof StringTag s4 ? s4.getAsString() : "";

			String lowerSearch = search.toLowerCase();

			// Check cache for the pseudo
			String playerName = uuidToNameCache.get(uuid);

			if (playerName != null) {
				// Filtrer avec search (numéro, nom, pseudo)
				if (phoneKey.toLowerCase().contains(lowerSearch)
						|| name.toLowerCase().contains(lowerSearch)
						|| playerName.toLowerCase().contains(lowerSearch)) {
					MutableComponent message = buildPhoneChatMessage(name, password, skin, uuid, phoneKey, playerName);
					player.sendSystemMessage(message);
				}
			} else {
				// Cache miss, fetch asynchrone
				fetchPlayerNameFromUUIDAsync(uuid).thenAccept(fetchedName -> {
					String finalName = (fetchedName != null && !fetchedName.isEmpty()) ? fetchedName : "Inconnu";
					uuidToNameCache.put(uuid, finalName);

					// Filtrer ici aussi avec la recherche
					if (phoneKey.toLowerCase().contains(lowerSearch)
							|| name.toLowerCase().contains(lowerSearch)
							|| finalName.toLowerCase().contains(lowerSearch)) {
						MutableComponent message = buildPhoneChatMessage(name, password, skin, uuid, phoneKey,
								finalName);
						player.getServer().execute(() -> player.sendSystemMessage(message));
					}
				});
			}
		}
	}

	public static CompletableFuture<String> fetchPlayerNameFromUUIDAsync(String uuid) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				String cleanUUID = uuid.replace("-", "");
				URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + cleanUUID);
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				connection.setRequestMethod("GET");
				connection.setConnectTimeout(5000);
				connection.setReadTimeout(5000);

				int responseCode = connection.getResponseCode();
				if (responseCode != 200) {
					return null; // Pas trouvé ou erreur
				}

				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				StringBuilder response = new StringBuilder();
				String line;
				while ((line = in.readLine()) != null) {
					response.append(line);
				}
				in.close();

				JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
				return json.get("name").getAsString();
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		});
	}

	private static MutableComponent buildPhoneChatMessage(String name, String password, String skin, String uuid,
			String number, String pseudo) {
		MutableComponent msg = Component.literal("");

		// Numéro (italic, gold)
		msg.append(Component.literal(number).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
		// Séparateur
		msg.append(Component.literal(" : ").withStyle(ChatFormatting.GRAY));

		// Nom (bold, green)
		msg.append(Component.literal(name).withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD));

		// Séparateur
		msg.append(Component.literal(" • ").withStyle(ChatFormatting.GRAY));

		// Mot de passe masqué
		String maskedPassword = "*".repeat(password.length());
		msg.append(Component.literal(maskedPassword)
				.withStyle(style -> style
						.withColor(ChatFormatting.YELLOW)
						.withBold(true)
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
								Component.literal("Cliquez pour copier le mot de passe : " + password)))
						.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, password))));

		// Séparateur
		msg.append(Component.literal(" • ").withStyle(ChatFormatting.GRAY));

		// Skin (gris clair, clic copie)
		msg.append(Component.literal("[Skin]")
				.withStyle(style -> style
						.withColor(ChatFormatting.GRAY)
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
								Component.literal("Cliquez pour copier l'apparence")))
						.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, skin))));

		// Séparateur
		msg.append(Component.literal(" • ").withStyle(ChatFormatting.GRAY));

		// Pseudo ou UUID (gris clair, clic copie)
		msg.append(Component.literal("[" + pseudo + "]")
				.withStyle(style -> style
						.withColor(ChatFormatting.GRAY)
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
								Component.literal("Cliquez pour copier l'UUID")))
						.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, uuid))));

		// Séparateur
		msg.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));

		// Bouton Donner (lime, gras, clic commande)
		msg.append(Component.literal("[GIVE]")
				.withStyle(style -> style
						.withColor(ChatFormatting.GREEN)
						.withBold(true)
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
								Component.literal("Cliquez pour prendre ce téléphone")))
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/crazyphone give " + number))));

		// Séparateur
		msg.append(Component.literal(" ").withStyle(ChatFormatting.GRAY));

		// Bouton Supprimer (rouge, gras, clic commande)
		msg.append(Component.literal("[X]")
				.withStyle(style -> style
						.withColor(ChatFormatting.RED)
						.withBold(true)
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
								Component.literal("Cliquez pour supprimer ce téléphone")))
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/crazyphone delete " + number))));

		return msg;
	}
}
