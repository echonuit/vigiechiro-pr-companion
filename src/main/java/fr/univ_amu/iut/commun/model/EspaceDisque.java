package fr.univ_amu.iut.commun.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/// Source de l'**espace disque disponible** (en octets) dans un dossier cible, isolée en interface pour
/// rendre les garde-fous **testables** : un test injecte une valeur basse (disque presque plein) sans
/// dépendre de l'état réel de la machine. Par défaut [#reel()].
///
/// **Seule lecture physique de l'espace disque de l'application.** Née dans `CompacteurDepot` pour le
/// garde-fou de génération des archives (#769), elle a été remontée ici quand l'import a eu besoin du
/// même service (#2041) : une feature ne peut pas dépendre d'une autre, et il n'y a aucune raison
/// d'appeler `getUsableSpace` à deux endroits.
///
/// ## Deux façons de traiter l'échec, et c'est voulu
///
/// Les appelants **sur le point d'écrire** laissent remonter l'`IOException` : un doute doit **refuser**
/// plutôt que de lancer une opération qui échouera à mi-parcours en laissant des fichiers partiels.
///
/// Les appelants qui **anticipent** (un libellé, un bouton grisé) traduisent l'échec en `0`, qui
/// signifie « inconnu » et ne bloque rien — c'est ce que fait `RepertoireDepot.espaceDisponible`. Le
/// même chiffre veut donc dire deux choses opposées selon le contexte, et chaque appelant doit dire
/// laquelle.
@FunctionalInterface
public interface EspaceDisque {

    /// Octets disponibles sur le système de fichiers hébergeant `dossier` (qui doit exister).
    long disponibleOctets(Path dossier) throws IOException;

    /// Espace réellement disponible sur le système de fichiers du dossier cible.
    static EspaceDisque reel() {
        return dossier -> Files.getFileStore(dossier).getUsableSpace();
    }
}
