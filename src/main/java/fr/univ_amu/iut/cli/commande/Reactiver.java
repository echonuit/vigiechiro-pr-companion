package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.commun.viewmodel.TexteCompteRendu;
import fr.univ_amu.iut.passage.model.CompteRenduReactivation;
import fr.univ_amu.iut.passage.model.IndiceAcoustique;
import fr.univ_amu.iut.passage.model.RapportReactivation;
import fr.univ_amu.iut.passage.model.RapportReactivation.EcartReactivation;
import fr.univ_amu.iut.passage.model.ServiceReactivationPassage;
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

    /// Le compte rendu partagé avec la modale, **suivi d'une ligne lisible par une machine**.
    ///
    /// Les deux tiennent parce qu'ils s'adressent à deux lecteurs. Le compte rendu est pour l'humain, et
    /// il vient de [CompteRenduReactivation] - le même que l'écran, donc les mêmes mots des deux côtés.
    /// La dernière ligne est pour le **script** : elle porte le nom de l'énumération (`COMPLETE`,
    /// `PARTIELLE`…), qui se grep et ne change pas quand une phrase est réécrite.
    ///
    /// La perdre en migrant vers le compte rendu aurait cassé le contrat scriptable sans qu'une phrase
    /// française y paraisse : « L'audio est de nouveau complet » ne se grep pas. `CliArchivageTest` l'a
    /// signalé (clôture #1990).
    private static String enTexte(RapportReactivation rapport) {
        String compteRendu = TexteCompteRendu.rendre(CompteRenduReactivation.de(rapport));
        return compteRendu + "\nAudio : " + rapport.decompte().disponibilite() + " ("
                + rapport.decompte().presentes() + "/" + rapport.decompte().total()
                + " séquence(s) sur disque).";
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
