const path = require('path');
module.exports = {
  extends: ['stylelint-config-standard', 'stylelint-config-prettier'],
  "plugins": ["stylelint-scss"],
  rules: {
    'max-empty-lines': 1,
    "at-rule-no-unknown": null,
    "scss/at-rule-no-unknown": true,
    "no-descending-specificity": null
  }
};
