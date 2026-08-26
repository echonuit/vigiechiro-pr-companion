package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.model.Horodatage;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ResultatImportMultiNuits;

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
                "Import terminé : %d séquence(s) produite(s) à partir de %d original(aux).",
                resultatMono.nombreSequences(), resultatMono.nombreOriginaux());
    }

    /// Récapitulatif multi-nuits : nombre de passages créés, plage de dates couverte, total de séquences.
    private static String statutNuits(ResultatImportMultiNuits resultat) {
        var passages = resultat.parNuit();
        // Le compte rendu TEXTUEL et le chiffré partagent leur titre (ADR 2358) : ils doivent donc
        // écrire la date de la même façon. Sans cela, la même nuit se lirait « 22/04/2026 » dans la
        // bande chiffrée et « 2026-04-22 » dans la barre de statut, sur le MÊME écran (#3950).
        //
        // Le commentaire d'origine disait « le compte rendu que la ligne de commande rend ».
        // C'était faux : cette classe n'a aucun consommateur côté CLI, elle alimente
        // `FormatsImport.libelle`, donc la barre de statut du wizard. Corrigé en #3991.
        String premiere = Horodatage.dateSeule(passages.getFirst().passage().dateEnregistrement());
        String derniere = Horodatage.dateSeule(passages.getLast().passage().dateEnregistrement());
        String plage = premiere.equals(derniere) ? "nuit du " + premiere : "nuits du " + premiere + " au " + derniere;
        return String.format(
                "Import terminé : %d passage(s) créé(s) (%s), %d séquence(s) produite(s).",
                resultat.nombrePassages(), plage, resultat.nombreSequencesTotal());
    }
}
