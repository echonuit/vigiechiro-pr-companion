package fr.univ_amu.iut.audit.model;

import fr.univ_amu.iut.commun.persistence.BilanSauvegarde;
import java.nio.file.Path;
import java.util.List;

/// Issue d'un **reset guidé** (#1419) : soit la procédure a **refusé de démarrer**, soit elle est allée
/// au bout. Deux issues, deux formes — plutôt qu'un rapport à trous qu'il faudrait interroger « est-ce
/// que ça s'est fait ? » avant de pouvoir le lire.
///
/// Chaque forme **porte son message** et son code de sortie : l'appelant (CLI ou IHM) affiche, il n'a
/// pas à savoir traduire un état en phrase.
public sealed interface ResultatReset {

    /// Code de sortie du CLI : `0` si la base a été remise à neuf, `2` si la procédure a refusé de
    /// démarrer (distinct de `1`, l'échec d'exécution).
    int codeSortie();

    /// Ce qu'il faut dire à l'utilisateur, en clair.
    String enClair();

    /// La procédure **n'a rien touché**. C'est le cas nominal quand quelque chose ne va pas : on refuse
    /// *avant* de détruire, jamais après.
    ///
    /// @param motif pourquoi on refuse, en clair
    /// @param bilan l'état de récupérabilité qui a motivé le refus (vide si le refus vient d'ailleurs)
    record Refuse(String motif, BilanRecuperabilite bilan) implements ResultatReset {

        @Override
        public int codeSortie() {
            return 2;
        }

        @Override
        public String enClair() {
            return "Reset refusé : " + motif + "\nRien n'a été modifié.";
        }
    }

    /// La base est neuve, et repeuplée depuis la plateforme.
    ///
    /// @param sauvegarde ce qui a été mis à l'abri avant destruction (et ce qui a manqué)
    /// @param filet copie de l'ancienne base (`vigiechiro.db.avant-reset`), relisible par `restaurer`
    /// @param passagesReconstruits nuits revenues du serveur, en passages **archivés**
    /// @param audit état du workspace après coup : il doit être sain
    /// @param aRetablir nuits dont l'audio doit être remis à la main (le serveur ne l'a pas)
    record Fait(
            BilanSauvegarde sauvegarde,
            Path filet,
            int passagesReconstruits,
            RapportAudit audit,
            List<String> aRetablir)
            implements ResultatReset {

        public Fait {
            aRetablir = List.copyOf(aRetablir);
        }

        @Override
        public int codeSortie() {
            return 0;
        }

        @Override
        public String enClair() {
            StringBuilder texte = new StringBuilder("Base remise à neuf.\n")
                    .append("  Sauvegarde : ")
                    .append(sauvegarde.enClair())
                    .append("\n  Filet : ")
                    .append(filet)
                    .append("\n  Reconstruit depuis VigieChiro : ")
                    .append(passagesReconstruits)
                    .append(" passage(s) archivé(s).\n  Audit final : ")
                    .append(audit.sain() ? "sain." : audit.nombre(SeveriteConstat.ERREUR) + " erreur(s).");
            if (!aRetablir.isEmpty()) {
                texte.append("\n\nIl reste l'audio à rétablir — le serveur ne l'a pas :");
                aRetablir.forEach(nuit -> texte.append("\n  - ").append(nuit));
                texte.append("\n  Depuis le disque : ./vigiechiro importer")
                        .append("\n  Depuis un passage archivé retrouvé : ./vigiechiro reactiver");
            }
            return texte.toString();
        }
    }
}
