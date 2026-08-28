package fr.univ_amu.iut.sites.model;

import java.util.List;

/// Ce qu'une position collée permet de proposer comme carré (#4577).
///
/// Le verdict porte son propre message, comme [VerdictCarre] et [LecturePosition] : chaque cas répond
/// pour lui-même plutôt qu'un `switch` chez l'appelant.
public sealed interface VerdictProposition {

    /// Message à afficher.
    String message();

    /// Le numéro à déposer dans le champ, ou **vide** quand il n'y a rien à déposer. C'est la seule
    /// question que le viewmodel pose au verdict : proposer un numéro, ou n'en proposer aucun.
    java.util.Optional<String> numeroAProposer();

    /// Un seul carré l'emporte nettement : son numéro se dépose.
    record Propose(String numero) implements VerdictProposition {
        @Override
        public String message() {
            return "Cette position tombe dans le carré " + numero + ".";
        }

        @Override
        public java.util.Optional<String> numeroAProposer() {
            return java.util.Optional.of(numero);
        }
    }

    /// La position est sur une **frontière** : plusieurs carrés sont candidats, et rien ne les
    /// départage. On les nomme, on n'en choisit aucun.
    ///
    /// Choisir reviendrait à tirer au sort : mesuré le 2026-08-27, deux centres sont à distance
    /// strictement égale au milieu d'un côté commun, et quatre le sont à un coin.
    record Frontiere(List<String> numeros) implements VerdictProposition {
        /// Rangés par **numéro**, et non par distance.
        ///
        /// Constaté à la revue visuelle de la clôture : le message dit qu'on ne choisit pas, et les
        /// listait pourtant du plus proche au plus lointain. L'ordre suggérait donc une préférence que
        /// la phrase refuse d'avoir - sur une frontière, les distances sont égales à quelques mètres,
        /// et ces mètres ne disent rien de l'endroit où était le micro.
        public Frontiere {
            numeros = numeros.stream().sorted().toList();
        }

        @Override
        public String message() {
            return "Cette position est sur la frontière entre les carrés " + String.join(" et ", numeros)
                    + ". Choisissez celui où se trouvait le micro.";
        }

        @Override
        public java.util.Optional<String> numeroAProposer() {
            return java.util.Optional.empty();
        }
    }

    /// Aucun carré ne couvre cette position : hors de France métropolitaine, ou coordonnées inversées.
    /// C'est une **réponse**, pas une panne.
    record HorsGrille() implements VerdictProposition {
        @Override
        public String message() {
            return "Aucun carré du carroyage national ne couvre cette position. Le carroyage s'arrête à la"
                    + " France métropolitaine ; vérifiez aussi l'ordre latitude puis longitude.";
        }

        @Override
        public java.util.Optional<String> numeroAProposer() {
            return java.util.Optional.empty();
        }
    }

    /// Le texte collé n'est pas une position. Le refus vient de [LecturePosition], qui sait déjà dire
    /// pourquoi : ce verdict ne fait que le porter.
    record PositionIllisible(LecturePosition refus) implements VerdictProposition {
        @Override
        public String message() {
            return refus.message();
        }

        @Override
        public java.util.Optional<String> numeroAProposer() {
            return java.util.Optional.empty();
        }
    }
}
