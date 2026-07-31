package fr.univ_amu.iut.analyse.model;

import fr.univ_amu.iut.commun.model.ContexteActivite;
import fr.univ_amu.iut.commun.model.Nuit;
import fr.univ_amu.iut.commun.model.ReferentielActivite;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/// Service de la **synthèse d'une nuit** (#2351, lot 1 du chantier #2348) : lit les observations d'un
/// passage et les confronte au référentiel d'activité.
///
/// Il ne fait que **lire et assembler** ; le calcul est dans [AgregationSynthese], pur et testable seul,
/// et le référentiel dans `commun/model`. Même découpage que [ServiceActivite] : le service lit, le
/// ViewModel réagit, le modèle calcule.
///
/// Le référentiel est chargé **une fois**, à la construction du service (singleton) : c'est une donnée
/// de référence embarquée, elle ne change pas en cours de session, et la relire par écran ouvert
/// laisserait croire le contraire.
public class ServiceSynthese {

    private final ProjectionsAudioDao projections;
    private final ReferentielActivite referentiel;

    public ServiceSynthese(ProjectionsAudioDao projections) {
        this(projections, ReferentielActivite.embarque());
    }

    /// Variante à **référentiel fourni**. Le référentiel devient un collaborateur au lieu d'une
    /// dépendance cachée, ce qui rend l'état « référentiel absent » atteignable autrement qu'en
    /// supprimant une ressource du jar : c'est ce qu'exerce `CaptureSyntheseSansReferentiel` (#3018).
    public ServiceSynthese(ProjectionsAudioDao projections, ReferentielActivite referentiel) {
        this.projections = Objects.requireNonNull(projections, "projections");
        this.referentiel = Objects.requireNonNull(referentiel, "referentiel");
    }

    /// La synthèse d'un passage.
    ///
    /// @param idPassage le passage à synthétiser
    /// @param validesSeulement ne retenir que les identifications validées ou corrigées
    /// @param nuit date de la nuit, pour en déduire la saison
    /// @param numeroCarre numéro du carré, pour en déduire la région
    /// @param milieu milieu **choisi** par l'observateur, ou `null` (comparaison nationale)
    public List<LigneSynthese> pour(long idPassage, boolean validesSeulement, String numeroCarre, String milieu) {
        List<LigneObservationAudio> lignes = projections.lignesAudioDuPassage(idPassage);
        return AgregationSynthese.de(
                lignes, validesSeulement, referentiel, ContexteActivite.de(nuitDe(lignes), numeroCarre, milieu));
    }

    /// La **nuit biologique** du passage, déduite de ses enregistrements plutôt que d'une date affichée :
    /// la bascule à midi ([Nuit#de], ADR 2352) est déjà la règle partout ailleurs, et une date lue dans
    /// une chaîne de présentation se casserait au premier changement de format.
    ///
    /// Vide si aucune séquence n'est horodatée : la comparaison se fera « toutes saisons », ce qui est
    /// plus large mais jamais faux.
    public LocalDate nuitDe(List<LigneObservationAudio> lignes) {
        return lignes.stream()
                .map(LigneObservationAudio::heureCapture)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .map(Nuit::de)
                .orElse(null);
    }

    /// La nuit biologique d'un passage, lue en base.
    public LocalDate nuitDuPassage(long idPassage) {
        return nuitDe(projections.lignesAudioDuPassage(idPassage));
    }

    /// Le contexte effectivement retenu, pour le **nommer à l'écran** et le recopier à l'export. Sans
    /// lui, la classe affichée serait un oracle : l'utilisateur ne saurait pas à quoi son nombre a été
    /// comparé.
    public ContexteActivite contexte(long idPassage, String numeroCarre, String milieu) {
        return ContexteActivite.de(nuitDuPassage(idPassage), numeroCarre, milieu);
    }

    /// Les milieux que le référentiel propose, pour peupler le sélecteur. Rendus **sans préfixe**
    /// (`Foret`, et non `habitat:Foret`) : c'est un choix offert à l'utilisateur, pas une clé technique.
    public List<String> milieuxDisponibles() {
        return referentiel.declinaisons().stream()
                .filter(d -> d.startsWith("habitat:"))
                .map(d -> d.substring("habitat:".length()))
                .distinct()
                .sorted()
                .toList();
    }

    /// Le référentiel est-il exploitable ? Faux si la ressource n'a rien donné : l'écran masque alors la
    /// colonne d'activité et le sélecteur de milieu au lieu d'afficher des cellules vides, et le tableau
    /// de comptages reste entièrement exploitable.
    public boolean referentielDisponible() {
        return referentiel.taille() > 0;
    }
}
