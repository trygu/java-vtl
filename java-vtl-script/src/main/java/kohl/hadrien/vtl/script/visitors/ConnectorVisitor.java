package kohl.hadrien.vtl.script.visitors;

/*-
 * #%L
 * java-vtl-script
 * %%
 * Copyright (C) 2016 Hadrien Kohl
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.google.common.base.Throwables;
import kohl.hadrien.vtl.connector.Connector;
import kohl.hadrien.vtl.connector.ConnectorException;
import kohl.hadrien.vtl.model.Dataset;
import kohl.hadrien.vtl.parser.VTLBaseVisitor;
import kohl.hadrien.vtl.parser.VTLParser;
import org.antlr.v4.runtime.misc.NotNull;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A visitor that handles get and puts VTL operators.
 * <p>
 * It uses the list of dataset connectors and returns the first result.
 */
public class ConnectorVisitor extends VTLBaseVisitor<Dataset> {

    final List<Connector> connectors;

    public ConnectorVisitor(List<Connector> connectors) {
        this.connectors = checkNotNull(connectors, "list of connectors was null");
    }

    @Override
    public Dataset visitGetExpression(@NotNull VTLParser.GetExpressionContext ctx) {
        // TODO: Get the identifier.
        String identifier = "identifier";
        try {
            for (Connector connector : connectors) {
                if (!connector.canHandle(identifier)) {
                    continue;
                }
                return connector.getDataset(ctx.getText());
            }
        } catch (ConnectorException ce) {
            Throwables.propagate(ce);
        }
        return null;
    }

    @Override
    public Dataset visitPutExpression(@NotNull VTLParser.PutExpressionContext ctx) {
        // TODO: Get the identifier and the dataset.
        String identifier = "identifier";
        Dataset dataset = null;
        try {

            for (Connector connector : connectors) {
                if (!connector.canHandle(identifier)) {
                    continue;
                }
                return connector.putDataset(identifier, dataset);
            }
            return super.visitPutExpression(ctx);
        } catch (ConnectorException ce) {
            Throwables.propagate(ce);
        }
        return null;
    }
}
