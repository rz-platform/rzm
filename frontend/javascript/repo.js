function showTooltip(tooltipId, tooltipsToHide) {
    const tooltip = document.getElementById(tooltipId);

    if (getComputedStyle(tooltip).display == 'none') {
        tooltip.style.display = 'block';
    } else {
        tooltip.style.display = 'none';
    }

    // hide other tooltips
    Array.from(document.getElementsByClassName('tooltip')).forEach(tooltipToHide => {
        if (tooltipToHide.id != tooltipId) {
            tooltipToHide.style.display = 'none';
        }
    });
}

document.getElementById('create-folder-tooltip').onclick = () => showTooltip('add-folder-form');
document.getElementById('create-file-tooltip').onclick = () => showTooltip('add-file-form');
