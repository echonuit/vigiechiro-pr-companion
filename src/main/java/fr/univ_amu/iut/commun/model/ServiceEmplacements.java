package fr.univ_amu.iut.commun.model;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/// Lit et écrit **où vivent** l'espace de travail et la base (#1038, [ADR 1038]). C'est le versant
/// métier de l'onglet « Emplacements » des réglages et de la commande CLI équivalente : ni l'un ni
/// l'autre ne touche directement la [ConfigurationAmorcage].
///
/// ## Ce qu'il ne fait pas
///
/// Il ne **déplace rien**. Écrire un nouvel emplacement ne fait que changer le **pointeur** lu au
/// prochain démarrage : les données restent là où elles sont. Déplacer le pointeur de la base vers un
/// dossier vide fait démarrer l'application sur une base **neuve**, l'ancienne restant intacte à son
/// ancien emplacement. C'est à l'utilisateur de copier son `.db` s'il veut l'emporter - même principe
/// que pour son audio (ADR 0048).
///
/// L'accessibilité d'un dossier candidat s'éprouve avec [SondeAccessibilite], partagée avec les autres
/// gestes qui désignent une destination (sauvegarde, reset, export).
///
/// La configuration lue et écrite est celle de [ConfigurationAmorcage#dossier()], la même que
/// [Workspace#resolu()] consulte : les deux ne peuvent donc pas diverger.
public final class ServiceEmplacements {

    /// Les emplacements effectifs et leurs défauts, tels que l'écran les affiche.
    ///
    /// @param espaceDeTravail dossier de travail effectif au prochain démarrage
    /// @param base fichier de base effectif au prochain démarrage
    /// @param espaceDeTravailParDefaut ce que vaudrait l'espace de travail sans configuration
    /// @param baseParDefaut ce que vaudrait la base sans configuration
    /// @param journaux dossier des journaux applicatifs, sous l'espace de travail effectif
    /// @param personnalise `true` si une configuration d'amorçage est écrite (emplacements choisis)
    public record Emplacements(
            Path espaceDeTravail,
            Path base,
            Path journaux,
            Path espaceDeTravailParDefaut,
            Path baseParDefaut,
            boolean personnalise) {}

    /// Emplacements effectifs au prochain démarrage, avec leurs défauts et le fait qu'ils soient ou non
    /// personnalisés.
    public Emplacements emplacementsCourants() {
        Workspace effectif = Workspace.resolu();
        Workspace defaut = Workspace.parDefaut();
        boolean personnalise = !ConfigurationAmorcage.lue().equals(ConfigurationAmorcage.vide());
        return new Emplacements(
                effectif.racine(),
                effectif.cheminBaseDeDonnees(),
                effectif.dossierLogs(),
                defaut.racine(),
                defaut.cheminBaseDeDonnees(),
                personnalise);
    }

    /// Écrit le choix de l'utilisateur : le dossier de travail, et le dossier **où ranger la base** (le
    /// fichier `vigiechiro.db` y est nommé automatiquement). Prend effet au prochain démarrage.
    public void enregistrer(Path dossierEspaceDeTravail, Path dossierBase) throws IOException {
        Objects.requireNonNull(dossierEspaceDeTravail, "dossierEspaceDeTravail");
        Objects.requireNonNull(dossierBase, "dossierBase");
        Path base = dossierBase.resolve(Workspace.FICHIER_BASE);
        new ConfigurationAmorcage(Optional.of(dossierEspaceDeTravail), Optional.of(base))
                .enregistrerDans(ConfigurationAmorcage.dossier());
    }

    /// Efface la configuration d'amorçage : l'application retrouve ses emplacements par défaut au
    /// prochain démarrage. Sans effet si aucune configuration n'est écrite.
    public void reinitialiser() throws IOException {
        ConfigurationAmorcage.vide().enregistrerDans(ConfigurationAmorcage.dossier());
    }
}
