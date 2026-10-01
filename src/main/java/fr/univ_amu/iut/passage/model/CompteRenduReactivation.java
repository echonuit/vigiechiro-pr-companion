package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.RapportAncrage;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Detail;
import fr.univ_amu.iut.passage.model.RapportReactivation.AbsenceReactivation;
import fr.univ_amu.iut.passage.model.RapportReactivation.EcartReactivation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// Met en compte rendu ce qu'une réactivation a fait : ce qui est revenu et sur quelle preuve, ce qui a
/// été refusé et pourquoi, ce qui manque encore.
///
/// Vit ici, et non dans le ViewModel, parce qu'il a **deux** consommateurs : la modale, qui en fait des
/// nœuds JavaFX par `VueCompteRendu`, et la commande `reactiver`, qui en fait des lignes par
/// `TexteCompteRendu`. Une feature ne peut pas citer le `viewmodel` d'une autre - la construction devait
/// donc descendre pour être partagée (clôture #1990).
///
/// Avant ce déplacement, la ligne de commande **rédigeait sa propre narration** des mêmes faits. La
/// Javadoc du ViewModel affirmait pourtant le contraire : « la ligne de commande les rend tous - sans
/// que la mise en forme ait à être écrite deux fois ». Elle l'était.
public final class CompteRenduReactivation {

    private CompteRenduReactivation() {}

    /// Le compte rendu, **structuré** (ADR 0031) : un titre, une mise en contexte, les faits avec leurs
    /// détails, ce qu'il faut retenir.
    ///
    /// Il était jusqu'ici assemblé au `StringBuilder` et rendu dans un `Label` unique. La structure permet
    /// à chaque surface de décider ce qu'elle montre - la modale résume au-delà de cinq détails, la ligne
    /// de commande les rend tous - sans que la mise en forme ait à être écrite deux fois.
    public static CompteRendu de(RapportReactivation rapport) {
        if (rapport.voie() == VoieReactivation.RECONSTRUIT) {
            return reconstruit(rapport);
        }
        List<Constat> constats = new ArrayList<>();
        constats.add(Constat.de(faitReactivees(rapport), Severite.SUCCES));
        if (rapport.dejaPresentes() > 0) {
            constats.add(
                    Constat.de(rapport.dejaPresentes() + " séquence(s) étaient déjà sur le disque.", Severite.INFO));
        }
        if (rapport.introuvables() > 0) {
            constats.add(new Constat(
                    rapport.introuvables() + " séquence(s) restent introuvables dans ce dossier.",
                    Severite.ERREUR,
                    detailsAbsences(rapport.absences())));
        }
        if (!rapport.perdants().isEmpty()) {
            constats.add(new Constat(
                    faitPerdants(rapport),
                    Severite.AVERTISSEMENT,
                    rapport.perdants().stream()
                            .map(nom -> new Detail(nom, MOTIF_PERDANT))
                            .toList()));
        }
        ajouterEcarts(constats, rapport.ecarts());
        ajouterIndiceAcoustique(constats, rapport.indiceAcoustique());
        ajouterRapatriement(constats, rapport.rapatriement());
        return new CompteRendu(titre(rapport), preambule(rapport), constats, conclusion(rapport));
    }

    /// Titre du compte rendu : honnête aussi quand rien n'a pu être tenté (passage reconstruit, #1648).
    static String titre(RapportReactivation rapport) {
        if (rapport.voie() == VoieReactivation.RECONSTRUIT) {
            return "Passage reconstruit";
        }
        return rapport.complete() ? "Passage réactivé" : "Réactivation partielle";
    }

    static String preambule(RapportReactivation rapport) {
        return rapport.voie() == VoieReactivation.BRUTS
                ? "Ce dossier ne contenait que vos enregistrements bruts : les séquences d'écoute ont été"
                        + " régénérées à partir d'eux, puis vérifiées une à une."
                : "";
    }

    private static String faitReactivees(RapportReactivation rapport) {
        String preuve =
                rapport.confianceMinimale() == null ? "" : " (identité vérifiée : " + libelleConfiance(rapport) + ")";
        return rapport.reactivees() + " séquence(s) réactivée(s)" + preuve + ".";
    }

    /// Ce que vaut un perdant de collision absent du dossier (#5720), pour le détail qui le nomme.
    static final String MOTIF_PERDANT =
            "perdant d'une collision de noms, renommé en « _001 » à l'import et absent des dossiers découpés"
                    + " par un autre outil";

    /// **Les perdants de collision**, en une phrase lue par les deux comptes rendus (#5720). Elle reste
    /// conditionnelle sur Vigie-Chiro : le statut du passage ne dit pas quel outil a déposé la nuit.
    static String faitPerdants(RapportReactivation rapport) {
        return rapport.perdants().size() + " séquence(s) viennent d'enregistrements qui se chevauchent :"
                + " renommées en « _001 » à l'import, elles manquent aux dossiers découpés par un autre outil,"
                + " Kaleidoscope compris. Réactivez depuis les enregistrements bruts pour les réécouter. Si la"
                + " nuit a été déposée avec Kaleidoscope, Vigie-Chiro ne les a jamais reçues : elles n'auront"
                + " pas d'observations.";
    }

    static String conclusion(RapportReactivation rapport) {
        return rapport.complete()
                ? "L'audio est de nouveau complet : le passage est écoutable."
                : "L'audio reste incomplet : " + rapport.decompte().presentes() + " séquence(s) sur "
                        + rapport.decompte().total() + " présentes.";
    }

    /// **Ce qui manquait, nommé** (#1943). Deux situations tombaient dans le même compteur : un
    /// enregistrement absent du dossier, qui appelle une action de l'utilisateur, et une tranche non
    /// régénérée, qui est un défaut de notre côté.
    ///
    /// Les plus coûteuses d'abord : c'est par elles qu'on commence à chercher. Le **plafond d'affichage**
    /// n'est plus ici - il appartient à la surface (ADR 0031).
    private static List<Detail> detailsAbsences(List<AbsenceReactivation> absences) {
        return absences.stream()
                .sorted(Comparator.comparingInt(AbsenceReactivation::sequences)
                        .reversed()
                        .thenComparing(AbsenceReactivation::nomFichier))
                .map(absence -> new Detail(
                        absence.nomFichier(),
                        absence.motif() + (absence.sequences() > 1 ? " (" + absence.sequences() + " séquences)" : "")))
                .toList();
    }

    /// Les fichiers **refusés** : jamais rebranchés en silence, chacun avec son motif.
    private static void ajouterEcarts(List<Constat> constats, List<EcartReactivation> ecarts) {
        if (ecarts.isEmpty()) {
            return;
        }
        constats.add(new Constat(
                ecarts.size() + " fichier(s) portaient le bon nom mais n'étaient pas le bon audio : ils n'ont"
                        + " pas été rebranchés (les observations auraient pointé sur le mauvais son).",
                Severite.ERREUR,
                ecarts.stream()
                        .map(ecart -> new Detail(ecart.nomFichier(), ecart.motif()))
                        .toList()));
    }

    /// Concordance acoustique en **indice** (#1682) : purement informatif. Les tranches régénérées sont
    /// acceptées sur preuve structurelle ; cet indice dit seulement à quel point les cris attendus s'y
    /// retrouvent.
    private static void ajouterIndiceAcoustique(List<Constat> constats, IndiceAcoustique indice) {
        if (indice == null || !indice.estRenseigne()) {
            return;
        }
        constats.add(Constat.de(
                "Concordance acoustique (indice, non bloquant) : " + indice.concordantes() + " séquence(s) sur "
                        + indice.mesurees() + " présentent les cris attendus.",
                Severite.INFO));
    }

    /// Ce que la **phase d'ancrage** a rapatrié (#1904) : les identifiants plateforme, et avec eux les
    /// **échanges avec le validateur** (#1867). Muet sinon.
    private static void ajouterRapatriement(List<Constat> constats, RapportAncrage rapatriement) {
        if (rapatriement == null || rapatriement.estMuet()) {
            return;
        }
        constats.add(Constat.de(rapatriement.texte(), Severite.INFO));
    }

    /// Compte rendu **honnête** d'un passage reconstruit (#1648) : ni « introuvables », ni fausse promesse.
    /// On explique pourquoi rien n'a pu être relié, et que ce n'est pas la faute des fichiers de l'utilisateur.
    private static CompteRendu reconstruit(RapportReactivation rapport) {
        return new CompteRendu(
                titre(rapport),
                "Ce passage a été reconstruit depuis Vigie-Chiro : l'application connaît le nom de ses "
                        + rapport.decompte().total()
                        + " séquence(s), mais pas la correspondance avec vos fichiers d'origine, ni les"
                        + " empreintes nécessaires pour les régénérer.",
                List.of(Constat.de(
                        "Vos fichiers ne sont pas en cause : ils n'ont simplement pas pu être reliés. La"
                                + " réactivation depuis les enregistrements bruts n'est pas encore disponible"
                                + " pour ce type de passage.",
                        Severite.INFO)),
                "");
    }
    /// Libellé du niveau de confiance **le plus faible** obtenu : c'est lui qui qualifie honnêtement la
    /// réactivation entière.
    private static String libelleConfiance(RapportReactivation rapport) {
        return switch (rapport.confianceMinimale()) {
            case CERTITUDE -> "certitude (empreinte du contenu, ou preuves structurelle et acoustique concordantes)";
            case FORTE -> "forte (nom, taille et durée concordants ; pas d'empreinte en base pour aller plus loin)";
        };
    }
}
