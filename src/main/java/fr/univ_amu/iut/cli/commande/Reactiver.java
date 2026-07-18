package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.passage.model.IndiceAcoustique;
import fr.univ_amu.iut.passage.model.RapportReactivation;
import fr.univ_amu.iut.passage.model.RapportReactivation.EcartReactivation;
import fr.univ_amu.iut.passage.model.ServiceReactivationPassage;
import fr.univ_amu.iut.passage.model.VoieReactivation;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `reactiver` (#1304, EPIC #1297) : **réactive un passage archivé** en ligne de commande, équivalent de
/// l'action « Réactiver ce passage » de M-Passage (#1302). Confronte chaque fichier du dossier source aux
/// séquences attendues (cascade de vérification #1309 : empreinte, sinon structurelle et acoustique) et ne
/// rebranche **que ce qui est vérifié**. Un homonyme au contenu différent n'est jamais rebranché : il est
/// rapporté, avec son motif.
///
/// **Codes de sortie** (exploitables en script) : `0` l'audio est **entièrement** revenu · `1` réactivation
/// **partielle** (des séquences restent divergentes ou introuvables) · `1` également en cas de refus métier
/// (passage inconnu, jamais importé, dossier introuvable), le message disant lequel · `2` invocation
/// incorrecte.
@Command(
        name = "reactiver",
        description = "Réactive un passage archivé : réimporte les fichiers d'origine depuis un dossier, "
                + "après vérification que ce sont bien les mêmes (empreinte, ou preuves structurelle et "
                + "acoustique). Sortie 0 si l'audio est complet, 1 s'il reste des séquences non rebranchées.")
public final class Reactiver implements Callable<Integer> {

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Identifiant technique du passage à réactiver.")
    private Long idPassage;

    @Option(
            names = "--source",
            required = true,
            paramLabel = "<dossier>",
            description = "Dossier contenant les fichiers d'origine (exploré récursivement).")
    private Path source;

    @Option(names = "--json", description = "Émet le rapport au format JSON plutôt qu'en texte.")
    private boolean json;

    @Spec
    private CommandSpec spec;

    // Provider, non instance directe : picocli instancie les sous-commandes AVANT la migration du schéma
    // (cf. Auditer). On résout donc paresseusement, à l'exécution de la commande.
    private final Provider<ServiceReactivationPassage> service;

    @Inject
    public Reactiver(Provider<ServiceReactivationPassage> service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        // Lève RegleMetierException (passage inconnu, jamais importé, dossier introuvable) → code 1.
        RapportReactivation rapport = service.get().reactiver(idPassage, source, progres -> {});
        sortie.println(json ? FormatJson.objet(projeter(rapport)) : enTexte(rapport));
        return rapport.complete() ? 0 : 1;
    }

    /// Rendu **texte** : ce qui est revenu et sur quelle preuve, ce qui a été refusé et pourquoi, ce qui
    /// manque encore.
    private static String enTexte(RapportReactivation rapport) {
        if (rapport.voie() == VoieReactivation.RECONSTRUIT) {
            // Passage reconstruit depuis la plateforme (#1648) : ni empreinte ni fréquence d'acquisition en
            // base, donc rien à apparier ni à régénérer. On le DIT au lieu de prétendre « introuvables ».
            return "Passage reconstruit depuis Vigie-Chiro : le nom des "
                    + rapport.decompte().total()
                    + " séquence(s) est connu, mais pas la correspondance avec les fichiers d'origine ni les"
                    + " empreintes nécessaires pour les régénérer. La réactivation depuis les bruts n'est pas"
                    + " encore disponible pour ce type de passage.";
        }
        StringBuilder texte = new StringBuilder();
        if (rapport.voie() == VoieReactivation.BRUTS) {
            // L'utilisateur a le droit de savoir ce qu'on a fait de son dossier : ses tranches n'ont pas
            // été retrouvées, elles ont été RECALCULÉES depuis ses bruts (puis vérifiées comme les autres).
            texte.append("Ce dossier ne contenait que les enregistrements bruts : les séquences ont été")
                    .append(" régénérées à partir d'eux, puis vérifiées.\n");
        }
        texte.append(rapport.reactivees()).append(" séquence(s) réactivée(s)");
        if (rapport.confianceMinimale() != null) {
            texte.append(" (identité vérifiée, confiance ")
                    .append(rapport.confianceMinimale().name().toLowerCase(java.util.Locale.FRANCE))
                    .append(')');
        }
        texte.append(".\n");
        if (rapport.dejaPresentes() > 0) {
            texte.append(rapport.dejaPresentes()).append(" séquence(s) étaient déjà sur le disque.\n");
        }
        if (rapport.manquantes() > 0) {
            texte.append(rapport.manquantes()).append(" séquence(s) restent introuvables dans ce dossier.\n");
        }
        ajouterEcarts(texte, rapport.ecarts());
        IndiceAcoustique indice = rapport.indiceAcoustique();
        if (indice != null && indice.estRenseigne()) {
            texte.append("Concordance acoustique (indice, non bloquant) : ")
                    .append(indice.concordantes())
                    .append('/')
                    .append(indice.mesurees())
                    .append(" séquence(s) présentent les cris attendus.\n");
        }
        // Parité avec la modale (ADR 0014) : ce que la phase d'ancrage a rapatrié, dont les échanges avec
        // le validateur (#1867), se dit aussi en ligne de commande.
        if (!rapport.rapatriement().estMuet()) {
            texte.append(rapport.rapatriement().texte()).append('\n');
        }
        texte.append("Audio : ")
                .append(rapport.decompte().disponibilite())
                .append(" (")
                .append(rapport.decompte().presentes())
                .append('/')
                .append(rapport.decompte().total())
                .append(" séquence(s) sur disque).");
        return texte.toString();
    }

    /// Les fichiers **refusés** : jamais rebranchés en silence, chacun avec son motif.
    private static void ajouterEcarts(StringBuilder texte, List<EcartReactivation> ecarts) {
        if (ecarts.isEmpty()) {
            return;
        }
        texte.append(ecarts.size())
                .append(" fichier(s) portaient le bon nom mais n'étaient pas le bon audio (non rebranchés) :\n");
        ecarts.forEach(ecart -> texte.append("  - ")
                .append(ecart.nomFichier())
                .append(" : ")
                .append(ecart.motif())
                .append('\n'));
    }

    /// Projection JSON (clés stables pour les scripts).
    static Map<String, Object> projeter(RapportReactivation rapport) {
        Map<String, Object> objet = new LinkedHashMap<>();
        objet.put("voie", rapport.voie().name());
        objet.put("reactivees", rapport.reactivees());
        objet.put("divergentes", rapport.divergentes());
        objet.put("manquantes", rapport.manquantes());
        objet.put("dejaPresentes", rapport.dejaPresentes());
        objet.put(
                "confianceMinimale",
                rapport.confianceMinimale() == null
                        ? null
                        : rapport.confianceMinimale().name());
        objet.put("disponibiliteAudio", rapport.decompte().disponibilite().name());
        objet.put("sequencesPresentes", rapport.decompte().presentes());
        objet.put("sequencesTotal", rapport.decompte().total());
        // Le compte rendu du rapatriement d'ancrage (#1904) : chaîne vide quand la phase ne s'est pas
        // déclenchée. Présent dans la projection pour qu'elle reste le reflet complet du rapport.
        objet.put("rapatriement", rapport.rapatriement().texte());
        IndiceAcoustique indice = rapport.indiceAcoustique();
        objet.put("acoustiqueMesurees", indice == null ? 0 : indice.mesurees());
        objet.put("acoustiqueConcordantes", indice == null ? 0 : indice.concordantes());
        objet.put(
                "ecarts",
                rapport.ecarts().stream()
                        .map(ecart -> {
                            Map<String, Object> ligne = new LinkedHashMap<>();
                            ligne.put("fichier", ecart.nomFichier());
                            ligne.put("motif", ecart.motif());
                            return ligne;
                        })
                        .toList());
        return objet;
    }
}
