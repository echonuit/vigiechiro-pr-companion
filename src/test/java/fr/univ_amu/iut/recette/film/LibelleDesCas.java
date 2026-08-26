package fr.univ_amu.iut.recette.film;

import fr.univ_amu.iut.recette.MotifDeCas;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/// Le libellé d'un cas, tel que sa session le formule.
///
/// Remplace la fonction `libelle_du_cas` du script. Le gain n'est pas la lisibilité :
/// c'est que le repli d'une puce sur plusieurs lignes physiques se met à l'épreuve dans un test
/// unitaire, sans banc, sans ffmpeg et sans tesseract. Les défauts que le carton a connus (un
/// libellé qui s'arrête en plein mot, une dernière ligne de repli perdue) sont ici des cas de
/// `LibelleDesCasTest`, qui tournent en quelques millisecondes.
public final class LibelleDesCas {

    /// Une puce de cas : `- **S1-26** · le texte du cas`.
    ///
    /// La case à cocher est OPTIONNELLE, et son absence a coûté 47 libellés sur 392 : les
    /// sessions `s7-reglages` et `s10-le-poste-windows` écrivent
    /// `- [ ] **S10-01** · …`, forme que le motif d'origine ne reconnaissait pas. Le carton
    /// de ces cas serait alors sorti sans libellé, ce qui ne se voit qu'à l'œil sur le film.
    /// La grammaire d'une puce est celle de [MotifDeCas], et non une seconde ecriture (#4465).
    ///
    /// Les marqueurs sont CONSOMMES par la grammaire au lieu d'etre nettoyes ensuite : le libelle
    /// est ce qui la suit, quel qu'en soit le nombre. Il n'y a plus de nettoyage a rater.
    private static final Pattern PUCE = MotifDeCas.CAS;

    private static final Pattern SEPARATEUR_INITIAL = Pattern.compile("^·\\s*");
    private static final Pattern LIEN = Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");
    private static final Pattern NUMERO_FINAL = Pattern.compile("\\s*#\\d+\\s*$");

    /// Au-delà, le carton deviendrait une page. La coupe se fait sur un BLANC et s'annonce par des
    /// points de suspension : un libellé tranché en plein mot annonce autre chose que ce que le
    /// clip montre, et c'est précisément le défaut qu'une revue humaine avait relevé.
    private static final int LONGUEUR_MAX = 170;

    private final Map<String, String> parCas;

    private LibelleDesCas(Map<String, String> parCas) {
        this.parCas = parCas;
    }

    /// Le libellé du cas, ou vide si aucune session ne le rédige encore.
    public Optional<String> de(String cas) {
        return Optional.ofNullable(parCas.get(cas));
    }

    public int nombreDeCas() {
        return parCas.size();
    }

    /// Lit toutes les sessions d'un dossier. Un dossier absent rend un recueil VIDE plutôt qu'une
    /// erreur : un cas annoté avant d'être rédigé ne doit pas empêcher le film de se produire.
    public static LibelleDesCas depuis(Path dossierDesSessions) throws IOException {
        if (!Files.isDirectory(dossierDesSessions)) {
            return new LibelleDesCas(Map.of());
        }
        List<String> lignes = new ArrayList<>();
        try (Stream<Path> fichiers = Files.list(dossierDesSessions)) {
            List<Path> sessions = fichiers.filter(
                            p -> p.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();
            for (Path session : sessions) {
                lignes.addAll(Files.readAllLines(session, StandardCharsets.UTF_8));
                // Une session ne prolonge pas la puce de la précédente : la dernière ligne de
                // l'une ne doit pas se recoller à la première de l'autre.
                lignes.add("");
            }
        }
        return de(lignes);
    }

    public static LibelleDesCas de(List<String> lignes) {
        Map<String, String> trouves = new LinkedHashMap<>();
        String cas = null;
        StringBuilder texte = new StringBuilder();

        for (String ligne : lignes) {
            Matcher puce = PUCE.matcher(ligne);
            if (puce.lookingAt()) {
                deposer(trouves, cas, texte);
                cas = puce.group(1);
                // `lookingAt` et non `matches` : la grammaire partagee s'arrete a la fin des
                // marqueurs, et le libelle est precisement ce qu'elle laisse derriere elle.
                texte = new StringBuilder(ligne.substring(puce.end()));
                continue;
            }
            if (cas != null && prolongeLaPuce(ligne)) {
                texte.append(' ').append(ligne.strip());
                continue;
            }
            deposer(trouves, cas, texte);
            cas = null;
            texte = new StringBuilder();
        }
        deposer(trouves, cas, texte);
        return new LibelleDesCas(trouves);
    }

    /// Une suite de puce est indentée et ne commence pas elle-même une puce. Sans ce second garde,
    /// une liste imbriquée serait avalée par le cas qui la précède.
    private static boolean prolongeLaPuce(String ligne) {
        if (ligne.isBlank() || !ligne.startsWith(" ")) {
            return false;
        }
        String noyau = ligne.strip();
        return !noyau.startsWith("- ") && !noyau.startsWith("* ");
    }

    private static void deposer(Map<String, String> trouves, String cas, StringBuilder texte) {
        if (cas == null) {
            return;
        }
        String libelle = nettoyer(texte.toString());
        if (!libelle.isEmpty()) {
            trouves.put(cas, libelle);
        }
    }

    private static String nettoyer(String brut) {
        String texte = brut.strip();
        texte = SEPARATEUR_INITIAL.matcher(texte).replaceFirst("");
        texte = LIEN.matcher(texte).replaceAll("$1");
        texte = texte.replace("**", "");
        texte = NUMERO_FINAL.matcher(texte).replaceFirst("");
        return ecourter(texte.strip());
    }

    private static String ecourter(String texte) {
        if (texte.length() <= LONGUEUR_MAX) {
            return texte;
        }
        String debut = texte.substring(0, LONGUEUR_MAX);
        int dernierBlanc = debut.lastIndexOf(' ');
        if (dernierBlanc > LONGUEUR_MAX / 2) {
            debut = debut.substring(0, dernierBlanc);
        }
        return debut.stripTrailing() + "…";
    }
}
