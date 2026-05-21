# P3 - Vérifier l'enregistrement par échantillonnage 🎧

[← Retour au hub des parcours](index.md) · **Section B — Approfondissements** · ✅ MUST

> **Persona principal** : Marie / Karim / Samuel. **MoSCoW** : MUST. **Objectifs qualité visés** : [O4 Exactitude lecture audio](../../Objectifs%20qualités/Objectifs%20qualités/O4.md), [O7 Intégrité](../../Objectifs%20qualités/Objectifs%20qualités/O7.md).

Marie vient d'importer une nuit (parcours [P2](P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md)). Avant de la déposer sur Vigie-Chiro, elle veut **s'assurer que la nuit s'est bien passée et que la qualité audio est exploitable** : pas de saturation parasite, pas d'enregistrement vide, micro fonctionnel. C'est un **sound check global**, distinct de la validation taxonomique espèce par espèce qui viendra plus tard (parcours [P7](P7%20-%20Valider%20les%20résultats%20Tadarida.md)).

1. Marie ouvre la vue détail du passage qui vient d'être importé.
2. Elle clique sur l'onglet **« Vérifier l'enregistrement »**. L'application constitue automatiquement une **sélection d'écoute** : 10-30 séquences d'écoute réparties uniformément sur la nuit (méthode `RéparTemporel` par défaut, R12).
3. La sélection s'affiche sous forme de liste chronologique. Pour chaque séquence : horodatage, durée, fréquence dominante (indicative), bouton ▶ pour écouter.
4. Marie écoute quelques séquences à des moments différents de la nuit. Comme les séquences sont **déjà ralenties ×10 sur disque** (cf. R10), la lecture se fait à vitesse normale, sans transformation à la volée - l'audio est immédiatement audible pour l'oreille humaine.
5. Marie peut compléter sa sélection si elle en ressent le besoin (changer la méthode pour `Aléatoire`, augmenter la taille à 50 séquences, ou ajouter manuellement une séquence à un moment précis).
6. Une fois sa revue faite, elle saisit son **verdict global** dans un menu déroulant : `OK`, `Douteux`, `À jeter`. Elle peut compléter par un commentaire libre (« vent fort vers 02:00, sons à vérifier »).
7. Le passage passe au statut `Vérifié` et le verdict est mémorisé. Marie peut enchaîner sur la préparation du lot ([P4](P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md)).

## Règles métier visibles

- R12 : sélection d'écoute constituée automatiquement (méthode `RéparTemporel` par défaut).
- R13 : l'utilisateur reste responsable - aucun seuil minimum d'écoute imposé.
- R14 : un passage avec verdict `À jeter` ne peut pas être inclus dans un lot prêt à déposer (alerte bloquante au moment du parcours [P4](P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md)).
