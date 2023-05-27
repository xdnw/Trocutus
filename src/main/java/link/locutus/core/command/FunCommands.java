package link.locutus.core.command;

import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Default;
import link.locutus.util.FileUtil;
import link.locutus.util.Messages;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class FunCommands {

    private String[] lines;

    @Command(desc = "Get a random joke from the joke file")
    public String joke() {
        if (lines == null) {
            lines = Objects.requireNonNull(FileUtil.readFile("/fun/jokes.txt")).split("\\r?\\n");
        }
        return lines[ThreadLocalRandom.current().nextInt(lines.length)];
    }

    @Command(desc = "We are the borg")
    public String trorg(@Default String msg) {
        if (msg == null || msg.isEmpty()) {
            return Messages.SLOGAN;
        }

        String[] Bs = {"\uD835\uDDBB", "\uD835\uDDA1", "B", "b", "\u200A", "\u200B", "\uFEFF", "\u180E"};
        String[] Os = {"\uD835\uDDC8", "\uD835\uDE7E", "O", "o", "\u200A", "\u200B", "\uFEFF", "\u180E"};
        String[] Rs = {"\uD835\uDDCB", "\uD835\uDDB1", "R", "r", "\u200A", "\u200B", "\uFEFF", "\u180E"};
        String[] Gs = {"\uD835\uDDC0", "\uD835\uDDA6", "G", "g", "\u200A", "\u200B", "\uFEFF", "\u180E"};

        String[][] CODES = new String[][]{
                Bs,
                Os,
                Rs,
                Gs
        };

        StringBuilder output = new StringBuilder();
        String input = msg;
        while (!input.isEmpty()) {
            boolean found = false;
            int id = 0;
            for (int tmp = 0; tmp < 2; tmp++) {
                outer:
                for (String[] codei : CODES) {
                    for (int j = 0; j < codei.length; j++) {
                        String codeij = codei[j];
                        if (input.startsWith(codeij)) {
                            input = input.substring(codeij.length());
                            id += j << (tmp * 3);
                            found = true;
                            break outer;
                        }
                    }
                }
            }
            if (!found) {
                output.setLength(0);
                break;
            }
            if (id == 0) output.append(" ");
            else if (id <= 'z' + 1) output.append((char) ('a' + id - 1));
            else output.append('0' + id - 27);
        }
        if (output.length() != 0) {
            return "Output:\n" + output;
        }
        int i = 0;

        msg = msg.toLowerCase();
        int id = -1;
        for (char c : msg.toCharArray()) {
            if (c == ' ') id = 0;
            else if (Character.isLetter(c)) id = 1 + (c - 'a');
            else if (Character.isDigit(c)) id = 26 + 1 + '0';

            if (id == -1) continue;


            for (int j = 0; j < 2; j++) {
                int letterId = id & 0x7;
                id = id >> 3;

                String letter = CODES[i % 4][letterId];
                if (letterId < 4) i++;
                output.append(letter);
            }
        }
        return "Output:\n" + output;
    }
}
