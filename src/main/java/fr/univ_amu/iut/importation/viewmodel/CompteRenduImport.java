package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Detail;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation.Severite;
import fr.univ_amu.iut.importation.model.RapportImport;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ResultatImportMultiNuits;
import fr.univ_amu.iut.importation.model.StatutImportFichier;
import java.util.ArrayList;
import java.util.List;

/// Restitution d'un import, **séparée en deux natures** (ADR 0028 / 0031).
///
/// - [#statut] : la phrase de la **barre de statut**, bornée par construction. Elle dit le volume traité,
///   rien de plus - c'est tout ce qu'une barre d'une ligne peut porter honnêtement.
/// - [#de] : le **compte rendu**, extensible : doublon de nuit, fichiers ignorés, fichiers rejetés,
///   anomalies du journal du capteur.
///
/// Auparavant, `RecapImport` (côté vue) concaténait les deux : la phrase de statut se terminait par
/// `rapport().avertissements()`, de longueur non bornée, dans la surface la plus étroite de l'écran. Un
/// doublon de nuit y déversait la liste des passages déjà présents. La troncature n'était pas décidée,
/// elle était infligée par la mise en page.
///
/// Vit dans le `viewmodel` et non dans la vue : mettre en forme une restitution est une décision de
/// présentation **testable**, et elle l'était d'autant moins qu'elle était enfouie dans un helper de vue.
public final class CompteRenduImport {

    private CompteRenduImport() {}

    /// Phrase de la barre de statut : annulation, import multi-nuits, import mono-nuit. Vide tant que
    /// l'import n'a pas abouti (en cours, prêt) - une barre de statut n'a pas à commenter l'attente.
    public static String statut(
            EtatImport etat, ResultatImport resultatMono, ResultatImportMultiNuits resultatMultiNuits) {
        if (etat == EtatImport.ANNULE) {
            return "Opération annulée.";
        }
        if (etat != EtatImport.TERMINE) {
            return "";
        }
        if (resultatMultiNuits != null) {
            return statutNuits(resultatMultiNuits);
        }
        if (resultatMono == null) {
            return "";
        }
        return String.format(
                "✓ Import terminé : %d séquence(s) produite(s) à partir de %d original(aux).",
                resultatMono.nombreSequences(), resultatMono.nombreOriginaux());
    }

    /// Récapitulatif multi-nuits : nombre de passages créés, plage de dates couverte, total de séquences.
    private static String statutNuits(ResultatImportMultiNuits resultat) {
        var passages = resultat.parNuit();
        String premiere = passages.getFirst().passage().dateEnregistrement();
        String derniere = passages.getLast().passage().dateEnregistrement();
        String plage = premiere.equals(derniere) ? "nuit du " + premiere : "nuits du " + premiere + " au " + derniere;
        return String.format(
                "✓ Import terminé : %d passage(s) créé(s) (%s), %d séquence(s) produite(s).",
                resultat.nombrePassages(), plage, resultat.nombreSequencesTotal());
    }

    /// Le compte rendu d'un import abouti. Vide si l'import est nominal : nuit neuve, aucun rejet, aucun
    /// fichier ignoré, aucune anomalie au journal - il n'y a alors rien à rapporter que le statut ne dise.
    public static CompteRendu de(ResultatImport resultat) {
        return resultat == null ? VIDE : deToutes(List.of(resultat));
    }

    /// Le compte rendu d'un import **multi-nuits** : les mêmes catégories, agrégées sur toutes les nuits.
    ///
    /// Rendre compte de la seule première nuit (celle que `premier()` expose par compatibilité) tairait
    /// les rejets et les doublons des autres - or c'est précisément sur un import multi-nuits qu'il y en
    /// a le plus.
    public static CompteRendu de(ResultatImportMultiNuits resultat) {
        return resultat == null ? VIDE : deToutes(resultat.parNuit());
    }

    private static final CompteRendu VIDE = CompteRendu.de("", List.of());

    private static CompteRendu deToutes(List<ResultatImport> nuits) {
        List<RapportImport> rapports =
                nuits.stream().map(ResultatImport::rapport).toList();
        List<Constat> constats = new ArrayList<>();
        ajouterDoublons(constats, rapports);
        ajouterCardinal(
                constats,
                somme(rapports, StatutImportFichier.IGNORE),
                "%d fichier(s) non pertinent(s) ignoré(s).",
                Severite.INFO);
        ajouterCardinal(
                constats,
                somme(rapports, StatutImportFichier.REJETE),
                "%d fichier(s) rejeté(s) : détail ci-dessous.",
                Severite.ERREUR);
        ajouterAnomalies(
                constats,
                nuits.stream().flatMap(nuit -> nuit.anomalies().stream()).toList());
        // Sans constat, on rend le compte rendu VIDE et non un titre seul : un titre sans rien dessous se
        // rendrait quand même (`estVide` exige aussi un titre blanc) et laisserait un cadre creux sous la
        // barre de statut, à lire comme un import qui aurait quelque chose à se reprocher.
        return constats.isEmpty() ? VIDE : CompteRendu.de("Rapport d'import", constats);
    }

    private static long somme(List<RapportImport> rapports, StatutImportFichier statut) {
        return rapports.stream().mapToLong(rapport -> rapport.compte(statut)).sum();
    }

    /// Doublon de nuit (#214/#147) : la nuit était déjà en base, l'utilisateur a choisi d'importer quand
    /// même. Chaque passage déjà présent est un détail - ils tenaient auparavant dans la phrase, joints
    /// par des virgules, sans que rien ne borne leur nombre.
    ///
    /// Le libellé d'un passage vient de [AvertissementsInspection#detail] : la même donnée était mise en
    /// forme deux fois, avant l'import et après, avec deux rédactions différentes (#2050).
    private static void ajouterDoublons(List<Constat> constats, List<RapportImport> rapports) {
        List<Detail> doublons = rapports.stream()
                .flatMap(rapport -> rapport.doublonsDeNuit().stream())
                .map(AvertissementsInspection::detail)
                .toList();
        if (!doublons.isEmpty()) {
            constats.add(new Constat("Doublon : cette nuit était déjà importée.", Severite.ERREUR, doublons));
        }
    }

    /// Un constat de dénombrement, **s'il y a lieu** : annoncer « 0 rejeté » serait du bruit.
    ///
    /// Les rejets n'emportent que leur cardinal, sans leurs détails : c'est la seule entorse assumée au
    /// principe « un compte rendu porte tout ». La liste déroulante de l'écran affiche déjà chaque rejet
    /// avec sa raison, et une carte SD réelle peut en produire des centaines - qu'une pile d'étiquettes ne
    /// saurait pas faire défiler. Reprendre les cinq premiers ici les montrerait deux fois.
    private static void ajouterCardinal(List<Constat> constats, long combien, String gabarit, Severite severite) {
        if (combien > 0) {
            constats.add(Constat.de(String.format(gabarit, combien), severite));
        }
    }

    /// Anomalies relevées dans le **journal du capteur** (R19). Elles étaient transportées jusqu'au
    /// ViewModel depuis `journal.messagesAnomalies()` et **aucune vue ne les affichait** : le champ
    /// existait, documenté « pour que l'IHM affiche un récapitulatif », et personne ne le lisait.
    private static void ajouterAnomalies(List<Constat> constats, List<String> anomalies) {
        if (!anomalies.isEmpty()) {
            constats.add(new Constat(
                    String.format("%d anomalie(s) relevée(s) au journal du capteur.", anomalies.size()),
                    Severite.INFO,
                    anomalies.stream().map(Detail::de).toList()));
        }
    }
}
