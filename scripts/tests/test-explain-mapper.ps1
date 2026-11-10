#requires -Version 7.2
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$settings = [Xml.XmlReaderSettings]::new()
$settings.DtdProcessing = [Xml.DtdProcessing]::Ignore
$settings.XmlResolver = $null
$reader = [Xml.XmlReader]::Create((Join-Path $root 'seatkeeper-service/src/main/resources/mapper/CatalogMapper.xml'), $settings)
try {
    $document = [Xml.XmlDocument]::new()
    $document.Load($reader)
} finally { $reader.Dispose() }
$node = $document.SelectSingleNode('/mapper/select[@id="listSeats"]')
$where = (($node.SelectSingleNode('where').SelectNodes('if') | ForEach-Object { $_.InnerText }) -join ' ').Replace('#{room}', "'第一阅览室'").Replace('#{floor}', '1')
$filtered = 'EXPLAIN ' + $node.FirstChild.Value + ' WHERE ' + $where + $node.LastChild.Value
$all = 'EXPLAIN ' + $node.FirstChild.Value + $node.LastChild.Value
$scriptText = [regex]::Replace([IO.File]::ReadAllText((Join-Path $root 'scripts/explain.ps1')), '\s+', ' ')
foreach ($expected in @($filtered, $all)) {
    $expected = [regex]::Replace($expected, '\s+', ' ').Trim()
    if (-not $scriptText.Contains($expected + ';')) { throw ('MissingExactMapperExplain: ' + $expected) }
}
Write-Output 'PASS CatalogMapper listSeats filtered and unfiltered SQL match EXPLAIN statements exactly.'
