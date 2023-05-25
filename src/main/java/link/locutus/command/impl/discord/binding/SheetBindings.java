package link.locutus.command.impl.discord.binding;


import link.locutus.command.binding.BindingHelper;
import link.locutus.command.binding.annotation.Binding;
import link.locutus.util.StringMan;
import link.locutus.util.spreadsheet.SpreadSheet;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Set;

public class SheetBindings extends BindingHelper {
    @Binding(value = "A google spreadsheet id or url", examples = {"sheet:1X2Y3Z4", "https://docs.google.com/spreadsheets/d/1X2Y3Z4/edit#gid=0"})
    public SpreadSheet sheet(String input) throws GeneralSecurityException, IOException {
        String spreadsheetId;
        if (input.startsWith("sheet:")) {
        } else if (input.startsWith("https://docs.google.com/spreadsheets/")) {
        } else {
            throw new IllegalArgumentException("Invalid sheet: `" + input + "`");
        }
        return SpreadSheet.create(input);
    }
}
