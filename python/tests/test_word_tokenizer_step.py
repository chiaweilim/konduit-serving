import pytest
from konduit import *
from konduit.load import server_from_file, client_from_file
import numpy as np

@pytest.mark.integration
def test_word_tokenizer_serving_from_file():

    file_path = "yaml/konduit_word_tokenizer_minimal.yaml"
    server = server_from_file(file_path)
    try:
        running_server = server_from_file(file_path, start_server=True)
    finally:
        running_server.stop()
