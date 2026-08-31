package fr.univ_amu.iut.importation.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Reconstitue les **cycles d'acquisition** (une nuit chacun) depuis les lignes brutes du journal
/// `LogPR` : le firmware ouvre chaque nuit par `Wakeup by ALARM... Cpt N` et la referme par
/// `### Passage en mode Veille` / `Mise en veille`. Un cycle refermé par une **anomalie** (carte SD
/// pleine, `### Passage en mode Erreur`) ou **jamais refermé** (journal interrompu, ou réveil suivant
/// sans veille) est marqué **incomplet** : c'est le signal fiable d'une **nuit tronquée**.
///
/// Parsing purement regex/positionnel, **déterministe**. Les évènements de configuration antérieurs au
/// premier réveil (tests d'accès SD, mises en veille de paramétrage) sont ignorés.
final class CyclesJournal {

    /// `JJ/MM/AA - HH:MM:SS PR<serie> <message>`, capture la date, l'heure **et** le message.
    private static final Pattern LIGNE =
            Pattern.compile("^(\\d{2})/(\\d{2})/(\\d{2})\\s*-\\s*(\\d{2}):(\\d{2}):(\\d{2})\\s+PR\\d+\\s+(.*)$");

    private static final Pattern CPT = Pattern.compile("Cpt\\s+(\\d+)");

    private CyclesJournal() {}

    /// Les cycles trouvés, dans l'ordre du journal (un par nuit), corrélables à une nuit détectée par
    /// [CycleAcquisition#dateNuit()].
    static List<CycleAcquisition> depuis(List<String> lignes) {
        List<CycleAcquisition> cycles = new ArrayList<>();
        Integer compteur = null;
        LocalDateTime reveil = null;
        boolean sdPleine = false;

        for (String brute : lignes) {
            Matcher m = LIGNE.matcher(brute.strip());
            if (!m.matches()) {
                continue;
            }
            LocalDateTime ts = LocalDateTime.of(
                    2000 + Integer.parseInt(m.group(3)),
                    Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(4)),
                    Integer.parseInt(m.group(5)),
                    Integer.parseInt(m.group(6)));
            String message = m.group(7).strip();
            String bas = message.toLowerCase(Locale.ROOT);

            // Un réveil VOULU ne commence pas une nuit : l'observateur a appuyé sur une touche pour
            // regarder son écran, et la nuit en cours continue. Le compter comme un cycle en
            // fabriquait une seconde et refermait la première en « tronquée » (#4981).
            if (MotifDeReveil.estUnReveil(message)
                    && bas.contains("cpt")
                    && !estUnReveilEnCoursDeNuit(message, reveil)) {
                // Nouveau réveil : un cycle précédent encore ouvert n'a jamais été refermé → tronqué.
                if (reveil != null) {
                    cycles.add(new CycleAcquisition(
                            compteur, reveil, null, false, motif(sdPleine, "réveil suivant sans mise en veille")));
                }
                Matcher c = CPT.matcher(message);
                compteur = c.find() ? Integer.parseInt(c.group(1)) : cycles.size() + 1;
                reveil = ts;
                sdPleine = false;
                continue;
            }
            if (reveil == null) {
                continue; // évènements de configuration avant le premier réveil : ignorés
            }
            if (bas.contains("place restante") || bas.contains("taille sd") || bas.contains("sd pleine")) {
                sdPleine = true;
            }
            if (bas.contains("mode veille") || bas.startsWith("mise en veille")) {
                cycles.add(new CycleAcquisition(compteur, reveil, ts, true, null));
                reveil = null;
                sdPleine = false;
            } else if (bas.contains("mode erreur") || bas.contains("carte sd pleine")) {
                cycles.add(new CycleAcquisition(compteur, reveil, ts, false, motif(sdPleine, "erreur du capteur")));
                reveil = null;
                sdPleine = false;
            }
        }
        if (reveil != null) {
            cycles.add(new CycleAcquisition(compteur, reveil, null, false, motif(sdPleine, "journal interrompu")));
        }
        return List.copyOf(cycles);
    }

    /// `true` si ce réveil survient **pendant** une nuit déjà ouverte et qu'il a été voulu.
    ///
    /// Les deux conditions comptent. Un réveil par touche AVANT toute nuit reste le début d'une nuit,
    /// puisque c'est ainsi qu'on met l'enregistreur en route ; et un réveil de cause inconnue referme
    /// la nuit en cours, parce que le journal ne sait pas ce qui s'est passé entre-temps.
    private static boolean estUnReveilEnCoursDeNuit(String message, LocalDateTime reveilOuvert) {
        return reveilOuvert != null && MotifDeReveil.estVoulu(message);
    }

    private static String motif(boolean sdPleine, String defaut) {
        return sdPleine ? "carte SD pleine" : defaut;
    }
}
