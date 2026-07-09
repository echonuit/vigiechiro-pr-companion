package fr.univ_amu.iut.commun.model;

import java.util.Objects;

/// Avancement **déterminé** d'une opération longue : un libellé lisible (« Compression 45/120 ») et une
/// fraction globale dans `[0, 1]`. Objet de transport **pur** (aucune dépendance JavaFX), émis par un
/// service au fil du travail et relayé par la couche IHM vers une barre de progression déterminée.
///
/// Vocabulaire **partagé** du socle : réutilisable par toute feature qui expose une opération longue
/// (génération des archives de dépôt #769, etc.).
///
/// @param libelle texte d'étape affichable (jamais `null`)
/// @param fraction avancement global, de `0.0` (rien fait) à `1.0` (terminé)
public record Progression(String libelle, double fraction) {

    public Progression {
        Objects.requireNonNull(libelle, "libelle");
    }
}
