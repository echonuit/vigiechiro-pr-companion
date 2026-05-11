# SC4 - Internationalisation (FR par défaut, EN en option)

Avec un **fichier de traduction approprié** remplaçant la langue par défaut, **tous les textes affichés et imprimés** par l'application apparaissent dans une langue tierce sans modification du code.

## Contexte

VigieChiro est un programme français mais s'inscrit dans un réseau européen de suivi des chauves-souris (programme [EuroBats](https://www.eurobats.org/)). Permettre une bascule simple en anglais ouvrirait la voie à un usage transfrontalier (Belgique, Suisse, Allemagne, Italie...).

## Critères d'acceptation

- Aucun texte affiché à l'utilisateur n'est codé en dur dans le code Java ou les FXML : tout passe par un mécanisme de bundle de ressources (`ResourceBundle`).
- Le fichier de traduction par défaut est `messages_fr.properties`. Un `messages_en.properties` complet permet la bascule en anglais.
- Le format des dates et des nombres respecte la locale courante (séparateur décimal `,` en français, `.` en anglais).
- L'encodage des fichiers de traduction est UTF-8 (caractères accentués gérés sans échappement).

## Objectif lié

Aucun objectif explicite dans la liste actuelle, mais ce scénario soutient indirectement [O2 - Facilité d'apprentissage](../Objectifs%20qualités/O2.md) (apprentissage facilité dans la langue maternelle de l'utilisateur).
