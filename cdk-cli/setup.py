from setuptools import setup

setup(
    name="oblak-cdk-cli",
    version="0.1.0",
    description="CLI klijent za Oblak platformu",
    py_modules=["main"],
    install_requires=[
        "click>=8.0",
        "requests>=2.28",
    ],
    entry_points={
        "console_scripts": [
            "cdk = main:cli",
        ],
    },
    python_requires=">=3.9",
)