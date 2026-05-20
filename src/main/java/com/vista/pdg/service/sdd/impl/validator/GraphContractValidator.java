package com.vista.pdg.service.sdd.impl.validator;

import com.vista.pdg.exception.InvalidContractException;
import com.vista.pdg.model.contract.GraphContract;
import org.springframework.stereotype.Component;

import java.util.List;

import com.vista.pdg.service.sdd.def.SpecificValidator;

@Component
public class GraphContractValidator implements SpecificValidator<GraphContract> {

    @Override
    public void validate(GraphContract g) {
        requireLabels(g);
        requireMatrix(g);
        validateDimensions(g);
        validateMatrixKind(g);
    }

    private void requireLabels(GraphContract g) {
        if (g.labels() == null || g.labels().isEmpty())
            throw new InvalidContractException("Graph must have at least one label");
    }

    private void requireMatrix(GraphContract g) {

        if (g.matrix() == null || g.matrix().data() == null)
            throw new InvalidContractException("Graph matrix data is required");
    }

    private void validateDimensions(GraphContract g) {
        List<List<Integer>> data = g.matrix().data();
        int n = g.labels().size();

        if (data.size() != n)
            throw new InvalidContractException(
                "Matrix row count (" + data.size() + ") != label count (" + n + ")");

        for (int i = 0; i < data.size(); i++)
            if (data.get(i).size() != n)
                throw new InvalidContractException(
                    "Matrix row " + i + " has " + data.get(i).size() + " columns, expected " + n);
    }

    private void validateMatrixKind(GraphContract g) {
        String kind = g.matrix().kind();
        if ("adjacency".equals(kind)) validateAdjacency(g);
        else if ("incidence".equals(kind)) validateIncidence(g);
    }

    private void validateAdjacency(GraphContract g) {
        if (g.directed()) return;
        List<List<Integer>> data = g.matrix().data();
        int n = g.labels().size();
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                if (!data.get(i).get(j).equals(data.get(j).get(i)))
                    throw new InvalidContractException(
                        "Asymmetric adjacency matrix at [" + i + "][" + j + "]");
    }

    private void validateIncidence(GraphContract g) {
        List<List<Integer>> data = g.matrix().data();
        int n = g.labels().size();
        int cols = data.get(0).size();
        int expected = g.directed() ? 0 : 2;
        for (int j = 0; j < cols; j++) {
            int sum = 0;
            for (int i = 0; i < n; i++) sum += data.get(i).get(j);
            if (sum != expected)
                throw new InvalidContractException(
                    "Incidence column " + j + " sums to " + sum + " (expected " + expected + ")");
        }
    }
}
