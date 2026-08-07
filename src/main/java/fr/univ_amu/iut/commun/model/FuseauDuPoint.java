package fr.univ_amu.iut.commun.model;

import com.google.inject.Inject;
import java.time.ZoneId;
import java.util.Objects;

/// Le fuseau des heures d'une nuit enregistrée sur un point donné (#3442).
///
/// Assemble les deux moitiés que ce chantier a séparées : le port [CommunePoint], qui dit **où** est le
/// point, et [FuseauDuSite#pour], qui dit quel fuseau porte ce territoire.
///
/// ```
/// point → commune (dérivée du GPS, ADR 2791) → département INSEE → fuseau
/// ```
///
/// ## Ce qu'il rend quand il ne sait pas
///
/// [FuseauDuSite#ZONE], c'est-à-dire l'heure de la métropole - exactement ce que le produit faisait
/// avant ce chantier. Un point sans GPS, une commune non encore résolue, un injecteur partiel sans
/// implémentation du port : aucun de ces cas ne dégrade quoi que ce soit, ils ne précisent simplement
/// pas.
///
/// ⚠️ **L'écriture et la lecture d'une même nuit doivent employer le MÊME fuseau.** Corriger un seul
/// des deux côtés déplace la nuit à chaque aller-retour : c'est le défaut que #1860 a payé de 21:00
/// descendu à 15:00 en quatre cycles, et que la CI a rattrapé sur #3434 quand la première version du
/// correctif de fuseau n'avait traité que l'écriture.
public class FuseauDuPoint {

    private final CommunePoint communes;

    @Inject
    public FuseauDuPoint(CommunePoint communes) {
        this.communes = Objects.requireNonNull(communes, "communes");
    }

    /// Le fuseau du point `idPoint`, ou celui de la métropole si rien ne permet de conclure.
    public ZoneId pour(Long idPoint) {
        return FuseauDuSite.pour(communes.pour(idPoint).orElse(null));
    }
}
