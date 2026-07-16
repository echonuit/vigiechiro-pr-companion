package fr.univ_amu.iut.cli.commande;

import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;

/// Garde commune aux commandes d'import de la CLI (`importer-tadarida` et `importer-vigiechiro`) : hors
/// remplacement, refuse un import sur un passage **déjà pourvu d'un jeu**, avec un message identique de
/// part et d'autre.
///
/// La règle « un seul jeu par passage » est portée par le service (garde-fou #1657) ; on la traduit ici
/// en **erreur d'usage** (code de sortie 2) avec le geste qui va bien en ligne de commande, « relancez
/// avec --remplacer », plutôt que de laisser remonter le message d'IHM du service (« ouvrez Sons &
/// validation »), inadapté hors interface. Les deux surfaces refusent alors de la même façon, avant tout
/// travail (côté VigieChiro, avant même l'appel réseau).
final class GardeJeuExistant {

    private GardeJeuExistant() {}

    /// Refuse l'import si le passage a déjà un jeu et que le remplacement n'est pas demandé.
    ///
    /// @throws ErreurUsage (code de sortie 2) si `idPassage` a déjà un jeu de résultats et que
    ///     `remplacer` est faux
    static void refuserSiDejaImporte(ResultatsIdentificationDao resultatsDao, Long idPassage, boolean remplacer) {
        if (!remplacer && resultatsDao.findByPassage(idPassage).isPresent()) {
            throw new ErreurUsage("Ce passage a déjà des résultats Tadarida."
                    + " Relancez avec --remplacer pour remplacer le jeu existant.");
        }
    }
}
