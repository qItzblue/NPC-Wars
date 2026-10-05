package com.npcwars.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.npcwars.chat.ChatRouting.Candidate;
import com.npcwars.chat.ChatTurn.Role;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatPureTest {

    // ---- ChatText

    @Test
    void cleanRemovesMarkdownNewlinesColourCodesAndQuotes() {
        assertEquals("hey there, whats up", ChatText.clean("\"**hey** there,\n\n_whats_  up\"", 200));
        assertEquals("no codes here", ChatText.clean("§cno §lcodes here", 200));
        assertEquals("", ChatText.clean(null, 200));
        assertEquals("", ChatText.clean("   \n ", 200));
    }

    @Test
    void cleanCutsLongRepliesAtASentenceOrWord() {
        String text = "I like mining a lot of iron. I also like building big houses near the spawn area.";
        String cut = ChatText.clean(text, 40);
        assertTrue(cut.length() <= 40, cut);
        assertEquals("I like mining a lot of iron.", cut);
        String noSentence = ChatText.clean("word ".repeat(30).strip(), 22);
        assertTrue(noSentence.length() <= 22 && !noSentence.endsWith(" "), noSentence);
        assertTrue(noSentence.endsWith("word"), noSentence);
    }

    @Test
    void mentionsMatchWholeWordsOnly() {
        assertTrue(ChatText.mentions("Kai", "hey kai, got any iron?"));
        assertTrue(ChatText.mentions("Pixel_Pete", "PIXEL_PETE come here"));
        assertFalse(ChatText.mentions("Kai", "kaiju attack"));
        assertFalse(ChatText.mentions("Ash", "fresh bread"));
        assertTrue(ChatText.mentions("Mining Mike", "thanks Mining Mike!"));
        assertFalse(ChatText.mentions("", "anything"));
        assertFalse(ChatText.mentions("a.b", "axb"), "names are matched literally, not as regex");
    }

    // ---- ChatMemory

    @Test
    void memorySnapshotsStartWithAUserTurnAndAlternate() {
        ChatMemory memory = new ChatMemory(10);
        memory.add(1, Role.ASSISTANT, "orphan reply");
        memory.add(1, Role.USER, "Steve: hi");
        memory.add(1, Role.USER, "Alex: hello?");
        memory.add(1, Role.ASSISTANT, "hey");
        memory.add(1, Role.USER, "Steve: nice");
        List<ChatTurn> turns = memory.snapshot(1);
        assertEquals(3, turns.size());
        assertEquals(Role.USER, turns.get(0).role());
        assertEquals("Steve: hi\nAlex: hello?", turns.get(0).text());
        assertEquals(Role.ASSISTANT, turns.get(1).role());
        assertEquals(Role.USER, turns.get(2).role());
    }

    @Test
    void memoryKeepsOnlyTheLatestTurnsAndForgets() {
        ChatMemory memory = new ChatMemory(3);
        for (int i = 0; i < 6; i++) {
            memory.add(7, i % 2 == 0 ? Role.USER : Role.ASSISTANT, "line " + i);
        }
        List<ChatTurn> turns = memory.snapshot(7);
        // lines 3,4,5 are kept; the leading assistant line is dropped so the conversation opens with a user turn
        assertEquals(2, turns.size());
        assertEquals("line 4", turns.get(0).text());
        assertEquals("line 5", turns.get(1).text());
        memory.forget(7);
        assertTrue(memory.snapshot(7).isEmpty());
        memory.add(8, Role.USER, "   ");
        assertTrue(memory.snapshot(8).isEmpty());
    }

    // ---- ChatRouting

    @Test
    void theNearestNpcInHearingRangeAnswers() {
        List<Candidate> candidates = List.of(new Candidate(1, 20, false, false), new Candidate(2, 6, false, false),
                new Candidate(3, 9, false, false));
        assertEquals(2, ChatRouting.pick(candidates, 12, 48));
        assertEquals(-1, ChatRouting.pick(List.of(new Candidate(1, 20, false, false)), 12, 48));
    }

    @Test
    void aNamedNpcAnswersEvenFarAwayAndNobodyElseDoes() {
        List<Candidate> candidates = List.of(new Candidate(1, 30, true, false), new Candidate(2, 3, false, false));
        assertEquals(1, ChatRouting.pick(candidates, 12, 48));
        // named but on cooldown: the near NPC must not jump in
        assertEquals(-1, ChatRouting.pick(List.of(new Candidate(1, 30, true, true), new Candidate(2, 3, false, false)), 12, 48));
        // named but too far even for mentions: normal rules apply
        assertEquals(2, ChatRouting.pick(List.of(new Candidate(1, 80, true, false), new Candidate(2, 3, false, false)), 12, 48));
    }

    @Test
    void npcsOnCooldownStayQuiet() {
        assertEquals(3, ChatRouting.pick(List.of(new Candidate(2, 3, false, true), new Candidate(3, 8, false, false)), 12, 48));
        assertEquals(-1, ChatRouting.pick(List.of(), 12, 48));
    }

    // ---- PromptBuilder

    @Test
    void promptNamesTheNpcIncludesThePersonaAndTheHonestyRule() {
        String prompt = PromptBuilder.system("Kai", "likes redstone");
        assertTrue(prompt.contains("You are Kai"));
        assertTrue(prompt.contains("likes redstone"));
        assertTrue(prompt.contains("AI-controlled NPC"));
        assertTrue(prompt.contains("PlayerName: message"));
        assertFalse(PromptBuilder.system("Kai", null).contains("About you"));
    }
}
