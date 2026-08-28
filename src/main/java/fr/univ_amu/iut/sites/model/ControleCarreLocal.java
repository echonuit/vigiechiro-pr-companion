package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.model.CarroyageNational;
import java.util.Objects;
import java.util.Optional;

/// Confronter un carré déclaré à une position **sans rien demander à personne** (#4682).
///
/// Même règle que [ControleCarreStoc] - toutes deux passent par [ConfrontationCarre] - mais l'autre
/// interroge la plateforme, et celle-ci lit le carroyage embarqué. « Quel carré couvre cette position »
/// est de la géométrie, et la géométrie se calcule hors ligne (ADR 4577) : le référentiel reproduit la
/// plateforme au centimètre.
///
/// C'est ce qui permet à la ligne de commande de contrôler ce que l'écran contrôle, sans jeton, sans
/// réseau, et sans changer la nature locale de `ajouter-point` et `modifier-point`.
public final class ControleCarreLocal {

    private final CarroyageNational carroyage;

    public ControleCarreLocal(CarroyageNational carroyage) {
        this.carroyage = Objects.requireNonNull(carroyage, "carroyage");
    }

    /// Le verdict pour une position, ou **vide** quand il n'y a rien à confronter : pas de carré déclaré,
    /// ou pas de coordonnées. Un point sans position est normal, et se taire est la bonne réponse.
    public Optional<VerdictCarre> confronter(String carreDeclare, Double latitude, Double longitude) {
        if (carreDeclare == null || carreDeclare.isBlank() || latitude == null || longitude == null) {
            return Optional.empty();
        }
        return Optional.of(ConfrontationCarre.confronter(carreDeclare, carroyage.candidats(latitude, longitude)));
    }
}
