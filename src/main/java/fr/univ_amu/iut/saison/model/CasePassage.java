package fr.univ_amu.iut.saison.model;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.passage.model.Passage;
import java.time.LocalDate;

/// État d'un des **deux passages attendus** d'un point pour une saison : soit **absent** (aucune
/// nuit enregistrée pour ce numéro), soit **présent** avec son statut de workflow, son verdict de
/// vérification et sa date. Les états et verdicts sont **repris du modèle existant** ; le solde ne
/// crée pas un second vocabulaire de statuts (critère de #2356).
///
/// `idPassage == null` marque l'absence ; les autres champs sont alors `null`.
///
/// Une nuit **opportuniste** (#2525, réalisée sur le carré d'un tiers) est **hors protocole** Point
/// Fixe : elle ne compte pas dans les « deux attendus » et n'engendre aucune action (cf. [#faite] et
/// le solde). Le solde la distingue visuellement plutôt que de la mêler au décompte.
///
/// @param idPassage identifiant de la nuit (pour l'ouverture au clic), `null` si absente
/// @param statut statut d'avancement de la nuit, `null` si absente
/// @param verdict verdict de vérification de la nuit, `null` si absente ou non vérifiée
/// @param date date d'enregistrement de la nuit, `null` si absente
/// @param opportuniste nuit hors protocole (carré d'un tiers) : présente mais hors décompte
/// @param campagne nom de la campagne de rattachement (#2355), ou `null` si la nuit n'est rattachée à
///     aucune. Sert au **filtrage** du solde par campagne ; le solde n'en fait pas une colonne (une ligne
///     porte deux passages, qui peuvent relever de campagnes différentes)
public record CasePassage(
        Long idPassage, StatutWorkflow statut, Verdict verdict, LocalDate date, boolean opportuniste, String campagne) {

    /// Case **absente** : aucune nuit enregistrée pour ce numéro de passage.
    public static CasePassage absente() {
        return new CasePassage(null, null, null, null, false, null);
    }

    /// Case **présente**, projetée depuis une nuit existante (avec sa nature opportuniste et sa campagne).
    /// La date est parsée depuis l'ISO `AAAA-MM-JJ` du passage (tolère une date absente, laissée à `null`).
    public static CasePassage de(Passage passage, boolean opportuniste, String campagne) {
        LocalDate date = passage.dateEnregistrement() == null ? null : LocalDate.parse(passage.dateEnregistrement());
        return new CasePassage(
                passage.id(), passage.statutWorkflow(), passage.verdictVerification(), date, opportuniste, campagne);
    }

    /// Vrai si une nuit existe pour ce numéro de passage.
    public boolean presente() {
        return idPassage != null;
    }

    /// Vrai si la nuit est jugée **inexploitable** ([Verdict#A_JETER]) : elle existe mais ne vaut
    /// rien pour le protocole, et compte donc comme un passage **à refaire**, pas comme un passage
    /// fait. C'est le cas où un décompte naïf induit en erreur.
    public boolean inexploitable() {
        return verdict == Verdict.A_JETER;
    }

    /// Vrai si la nuit **compte** comme réalisée dans le décompte de la saison : présente, pas
    /// inexploitable et **pas opportuniste** (une nuit sur le carré d'un tiers est hors protocole, cf.
    /// #2525). Une nuit encore en cours de traitement (non déposée) compte comme faite pour la présence,
    /// même s'il reste une action à mener dessus.
    public boolean faite() {
        return presente() && !inexploitable() && !opportuniste;
    }

    /// Vrai si la nuit est **terminée** au sens du protocole : déposée sur Vigie-Chiro, plus aucune
    /// action restante. Un passage déposé est nécessairement exploitable (R14 interdit de déposer une
    /// nuit inexploitable).
    public boolean terminee() {
        return presente() && statut == StatutWorkflow.DEPOSE;
    }
}
