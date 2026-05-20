package com.vista.pdg.service.sdd.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.exception.InvalidContractException;
import com.vista.pdg.model.contract.GraphContract;
import com.vista.pdg.model.contract.LatticeContract;
import com.vista.pdg.model.contract.RelationContract;
import com.vista.pdg.model.contract.StructureContract;
import com.vista.pdg.model.contract.TreeContract;
import org.springframework.stereotype.Component;

@Component
public class ContractBuilder {

    private final ObjectMapper mapper;
    private final ContractValidator validator;

    public ContractBuilder(ObjectMapper mapper, ContractValidator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    public StructureContract build(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            String type = root.path("type").asText();

            StructureContract contract = switch (type) {
                case "graph"    -> mapper.treeToValue(root, GraphContract.class);
                case "tree"     -> mapper.treeToValue(root, TreeContract.class);
                case "lattice"  -> mapper.treeToValue(root, LatticeContract.class);
                case "relation" -> mapper.treeToValue(root, RelationContract.class);
                default -> throw new InvalidContractException("Unknown type: \"" + type + "\"");
            };

            validator.validate(contract);
            return contract;

        } catch (InvalidContractException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidContractException("Failed to parse contract JSON: " + e.getMessage());
        }
    }
}
