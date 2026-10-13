package dev.duckvisual.module.impl.hud;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;

import java.util.Comparator;
import java.util.List;

/** Rounded sidebar scoreboard (replaces the vanilla one, InGameHudMixin). */
public class ScoreboardHud extends Module {
    private final BooleanSetting scores = add(new BooleanSetting("Show Scores", true));

    public ScoreboardHud() {
        super("ScoreboardHud", "Redesigned sidebar scoreboard", Category.HUD);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    public void render(DrawContext ctx, ScoreboardObjective objective) {
        Scoreboard sb = objective.getScoreboard();
        List<ScoreboardEntry> entries = sb.getScoreboardEntries(objective).stream()
                .filter(e -> !e.hidden())
                .sorted(Comparator.comparingInt(ScoreboardEntry::value).reversed().thenComparing(ScoreboardEntry::owner))
                .limit(15).toList();
        String title = objective.getDisplayName().getString();
        String[] names = new String[entries.size()];
        String[] values = new String[entries.size()];
        float w = Render2D.width(title) + 16;
        for (int i = 0; i < entries.size(); i++) {
            ScoreboardEntry e = entries.get(i);
            Team team = sb.getScoreHolderTeam(e.owner());
            Text name = Team.decorateName(team, e.name());
            names[i] = name.getString();
            values[i] = scores.get() ? e.formatted(objective.getNumberFormatOr(StyledNumberFormat.RED)).getString() : "";
            w = Math.max(w, Render2D.width(names[i]) + Render2D.width(values[i]) + 18);
        }
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        float h = 18 + entries.size() * 10 + 4;
        float x = sw - w - 4, y = sh / 2f - h / 2;
        Render2D.shadow(ctx, x, y, w, h, 5, 5, ColorUtil.withAlpha(ColorUtil.theme(), 70));
        Render2D.roundedRect(ctx, x, y, w, h, 5, 0xD8101015);
        Render2D.roundedGradient(ctx, x, y, w, 2, 1, ColorUtil.theme(0), ColorUtil.theme(10), ColorUtil.theme(10), ColorUtil.theme(0));
        Render2D.centeredText(ctx, title, x + w / 2, y + 6, ColorUtil.theme());
        float yy = y + 19;
        for (int i = 0; i < entries.size(); i++) {
            // keep the server's colors: draw the decorated text via vanilla formatting codes
            ctx.drawText(mc.textRenderer, Team.decorateName(sb.getScoreHolderTeam(entries.get(i).owner()), entries.get(i).name()),
                    (int) x + 7, (int) yy, 0xFFFFFFFF, false);
            if (!values[i].isEmpty()) Render2D.text(ctx, values[i], x + w - 6 - Render2D.width(values[i]), yy, 0xFFFF5555);
            yy += 10;
        }
    }
}
