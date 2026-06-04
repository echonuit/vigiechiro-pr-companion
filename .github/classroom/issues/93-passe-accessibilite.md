# ♿ [passe finale] Accessibilité et ergonomie (ISO 25010)

L'utilisabilité fait partie de la qualité (ISO 25010 : *operability*, *accessibility*). Une nuit de
terrain s'utilise parfois fatigué, au clavier : l'IHM doit rester opérable et lisible.

## Ce qu'il faut faire

Passez chaque écran que vous avez construit au crible :

- [ ] **Navigation clavier** : on peut atteindre les contrôles importants au `Tab` et les activer à
      `Entrée`/`Espace` ; les cartes cliquables (accueil, sites) sont *focusables* — comparez à la
      référence `sites`.
- [ ] **Affordance** : un bouton indisponible est **désactivé** (et non cliquable sans effet) ; un état
      verrouillé est expliqué (libellé, info-bulle), pas juste grisé.
- [ ] **Retour d'information** : chaque action produit un message clair (succès / erreur) ; les listes
      vides affichent un état explicite (« aucun… »), pas un vide muet.
- [ ] **Lisibilité** : libellés explicites, unités présentes, textes longs en `wrapText`, pas de
      troncature.
- [ ] **Cohérence** : mêmes conventions visuelles que la feature `sites` (styles, emojis, espacements).

## Critères d'acceptation

- [ ] Chaque écran construit est **entièrement opérable au clavier** pour ses actions principales.
- [ ] Aucun état « bloqué sans explication » ; tous les retours d'action sont visibles.

## Definition of Done

- [ ] Une **checklist d'accessibilité** par écran est remplie dans le compte rendu d'équipe.
- [ ] Les écarts trouvés ont été corrigés (via PR) ou tracés en issues s'ils sont hors périmètre.
