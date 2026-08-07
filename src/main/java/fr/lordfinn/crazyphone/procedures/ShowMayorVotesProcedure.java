package fr.lordfinn.crazyphone.procedures;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.utils.Contact;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

import java.util.*;

public class ShowMayorVotesProcedure {
	public static void execute(Level world, Entity entity) {
		if (!(entity instanceof Player player)) return;

		CompoundTag votes = PhoneRegistrySavedData.get(world).mayorVotes;

		// Compter les votes pour chaque numéro
		Map<String, Integer> voteCounts = new HashMap<>();
		for (String key : votes.getAllKeys()) {
			String candidateNumber = votes.get(key).getAsString();
			voteCounts.put(candidateNumber, voteCounts.getOrDefault(candidateNumber, 0) + 1);
		}

		// Trier par nombre de votes (décroissant)
		List<Map.Entry<String, Integer>> sortedVotes = new ArrayList<>(voteCounts.entrySet());
		sortedVotes.sort((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()));

		// Afficher les résultats
		if (sortedVotes.isEmpty()) {
			player.displayClientMessage(Component.literal("Aucun vote n'a encore été enregistré.")
				.withStyle(ChatFormatting.GRAY), false);
			return;
		}

		player.displayClientMessage(Component.literal("📊 Résultats des votes pour le maire :")
			.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

		for (Map.Entry<String, Integer> entry : sortedVotes) {
			String number = entry.getKey();
			int count = entry.getValue();

			Contact contact = CrazyPhoneHelper.getContact((Level)world, number);
			String displayName = contact != null ? contact.getName() : ("Numéro inconnu: " + number);

			player.displayClientMessage(
				Component.literal("• ").append(Component.literal(displayName)
					.withStyle(ChatFormatting.AQUA))
					.append(Component.literal(" - " + count + " vote(s)").withStyle(ChatFormatting.WHITE)),
				false
			);
		}
		// Ligne vide pour séparer
		player.displayClientMessage(Component.literal(""), false);

		player.displayClientMessage(Component.literal("🗳️ Détail des votes :")
			.withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), false);

		for (String voter : votes.getAllKeys()) {
			String candidateNumber = votes.get(voter).getAsString();

			Contact voterContact = CrazyPhoneHelper.getContact(world, voter);
			String voterName = voterContact != null ? voterContact.getName() : ("Inconnu: " + voter);

			Contact candidateContact = CrazyPhoneHelper.getContact(world, candidateNumber);
			String candidateName = candidateContact != null ? candidateContact.getName() : ("Numéro inconnu: " + candidateNumber);

			player.displayClientMessage(
				Component.literal("• ")
					.append(Component.literal(voterName).withStyle(ChatFormatting.GREEN))
					.append(Component.literal(" a voté pour "))
					.append(Component.literal(candidateName).withStyle(ChatFormatting.AQUA)),
				false
			);
		}
	}
}
