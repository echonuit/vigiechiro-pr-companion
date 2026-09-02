# Glossaire des outils & ressources externes

Outils tiers et ressources web mentionnés dans le brief et dans le reste du dossier d'analyse.

| Outil / ressource | Rôle | Statut dans le MVP |
|---|---|---|
| [Lupas Rename](https://www.lupinho.net/lupas-rename.html) | Outil tiers de renommage en lot, utilisé manuellement aujourd'hui pour appliquer le préfixe `Car…-AAAA-PassN-YY-` aux enregistrements originaux. | **Remplacé par l'app** (la chaîne d'import fait ce travail). |
| [Kaléidoscope 4.3.1](https://www.wildlifeacoustics.com/products/kaleidoscope-pro) | Logiciel commercial Wildlife Acoustics, utilisé manuellement aujourd'hui pour produire les séquences d'écoute (découpage 5 s + expansion temps ×10). | **Remplacé par l'app** (la chaîne d'import fait ce travail). |
| Tadarida (Bas et al., 2017) | Logiciel scientifique de classification automatique des taxons à partir des séquences d'écoute. Tourne **côté serveur Vigie-Chiro**. | Hors MVP côté code. **L'app produit ce que Tadarida attend** et **consomme ce que Tadarida restitue** (résultats d'identification). |
| [Chirosurf 4.1](https://vigie-chiro.forumactif.com/t108-chirosurf-4-1-telechargement-audible-ultrasons-basses-frequences-11-05-26) | Logiciel communautaire de validation taxonomique. | Référence d'**inspiration ergonomique** pour la validation taxonomique (SHOULD / cible étirable). |
| [vigiechiro.herokuapp.com](https://vigiechiro.herokuapp.com/) | Portail web officiel Vigie-Chiro. Création des sites de suivi, dépôt des séquences d'écoute, restitution des résultats d'identification. | L'application **dialogue avec l'API** de la plateforme : dépôt, synchronisation des sites, récupération des résultats, publication des corrections. Le dépôt navigateur reste un **repli** hors connexion. |
| [vigienature.fr](https://www.vigienature.fr/fr/le-protocole-en-detail) | Documentation officielle du protocole Point Fixe. | Référence de cadrage pour toutes les règles métier ci-dessus. |
| [PiBatRecorderProjects/TeensyRecorders](https://framagit.org/PiBatRecorderProjects/TeensyRecorders) | Projet open-source du firmware de l'enregistreur Teensy. | Référence technique pour comprendre le format du journal du capteur et des enregistrements originaux. Hors MVP côté code. |

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
