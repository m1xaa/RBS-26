"""
Setup za Oblak CDK CLI.

Instalacija u development modu:
    pip install -e .

Posle ovoga, komanda 'cdk' postaje dostupna globalno.
"""

from setuptools import setup, find_packages

setup(
    name="oblak-cdk-cli",
    version="0.1.0",
    description="CLI klijent za Oblak platformu",
    packages=find_packages(),
    install_requires=[
        "click>=8.0",
        "requests>=2.28",
    ],
    entry_points={
        "console_scripts": [
            "cdk = cdk_cli.main:cli",
        ],
    },
    python_requires=">=3.9",
)
